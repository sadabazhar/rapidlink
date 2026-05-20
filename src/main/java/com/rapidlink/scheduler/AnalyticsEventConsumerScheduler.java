package com.rapidlink.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidlink.dto.internal.ParsedAnalyticsRecord;
import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.entity.ClickEvent;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.mapper.ClickEventMapper;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ClickEventRepository;
import com.rapidlink.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads click events from Redis Stream and saves them to PostgreSQL.
 *
 * Main purpose:
 * - Keep redirect requests fast
 * - Process analytics separately in background
 * - Use Redis Stream as temporary event storage
 * - Support retries and failure recovery
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumerScheduler {

    private static final String STREAM_KEY = "analytics:click_events";
    private static final String GROUP_NAME = "analytics_group";
    private static final String CONSUMER_NAME = "analytics_consumer_1";
    private static final String RETRY_COUNT_PREFIX = "analytics:retry:";
    private static final String DLQ_STREAM_KEY = "analytics:dlq";

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration RETRY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final RapidLinkMetrics metrics;

    /**
     * Runs every 30 seconds.
     *
     * Processing order:
     * 1. Retry failed pending records first
     * 2. Process newly added records
     *
     * This prevents failed events from being ignored forever.
     */

    @Scheduled(fixedDelay = 30000)
    public void processAnalyticsEvents() {

        log.debug("[ANALYTICS] Starting analytics event processing cycle");

        try {
            processPendingRecords();
            processNewRecords();
        } catch (Exception ex) {
            log.error("[ANALYTICS] Stream processing failed", ex);
        }
    }

    /**
     * Processes old failed records that were delivered before
     * but were not successfully completed.
     *
     * These records stay in Redis Pending Entries List (PEL)
     * until they are either:
     * - Successfully saved
     * - Moved to DLQ
     */
    private void processPendingRecords() {

        try {

            List<MapRecord<String, Object, Object>> pendingRecords =
                    redisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty()
                                    .count(BATCH_SIZE),
                            StreamOffset.create(
                                    STREAM_KEY,
                                    ReadOffset.from("0")
                            )
                    );

            if (pendingRecords == null || pendingRecords.isEmpty()) {
                log.debug("[ANALYTICS] No pending analytics events");
                return;
            }

            metrics.recordAnalyticsPendingRead(pendingRecords.size());

            log.info("[ANALYTICS] Retrying {} pending analytics events",
                    pendingRecords.size());

            processRecordBatch(pendingRecords);

        } catch (Exception ex) {
            log.error("[ANALYTICS] Failed processing pending records", ex);
        }
    }

    /**
     * Processes fresh analytics events that were never read before.
     *
     * Only new stream messages are fetched here.
     */
    private void processNewRecords() {

        try {

            List<MapRecord<String, Object, Object>> newRecords =
                    redisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty()
                                    .count(BATCH_SIZE)
                                    .block(Duration.ofSeconds(2)),
                            StreamOffset.create(
                                    STREAM_KEY,
                                    ReadOffset.lastConsumed()
                            )
                    );

            if (newRecords == null || newRecords.isEmpty()) {
                log.debug("[ANALYTICS] No new analytics events");
                return;
            }

            metrics.recordAnalyticsEventsRead(newRecords.size());

            log.info("[ANALYTICS] Processing {} new analytics events",
                    newRecords.size());

            processRecordBatch(newRecords);

        } catch (Exception ex) {
            log.error("[ANALYTICS] Failed processing new records", ex);
        }
    }

    /**
     * Main batch processing logic.
     *
     * Steps:
     * 1. Parse incoming payloads
     * 2. Collect Short URL IDs
     * 3. Bulk fetch Short URLs
     * 4. Build ClickEvent entities
     * 5. Save events in batch
     * 6. ACK and delete successful records
     *
     * Failed records are retried later.
     */
    private void processRecordBatch(
            List<MapRecord<String, Object, Object>> records
    ) {


        if (records.isEmpty()) {
            return;
        }
        metrics.recordAnalyticsBatchSize(records.size());

        metrics.timeAnalyticsBatchProcessing(() ->{
            processRecordBatchInternal(records);
            return null;
        });

    }

    private void processRecordBatchInternal(List<MapRecord<String, Object, Object>> records) {
        // Parse records and collect valid Short URL IDs
        List<ParsedAnalyticsRecord> parsedRecords = new ArrayList<>();
        Set<UUID> shortUrlIds = new HashSet<>();

        for (MapRecord<String, Object, Object> record : records) {

            try {

                Object payloadObj = record.getValue().get("payload");

                if (payloadObj == null) {
                    acknowledgeAndDelete(record);
                    continue;
                }

                ClickEventRequest request = objectMapper.readValue(
                        payloadObj.toString(),
                        ClickEventRequest.class
                );

                parsedRecords.add(
                        new ParsedAnalyticsRecord(record, request)
                );

                shortUrlIds.add(request.shortUrlId());

            } catch (Exception ex) {

                metrics.recordAnalyticsProcessFailure();

                log.error(
                        "[ANALYTICS] Failed parsing event recordId={}",
                        record.getId(),
                        ex
                );

                handleFailure(record, ex);
            }
        }

        if (parsedRecords.isEmpty()) {
            return;
        }


        // Bulk load all related Short URLs in one DB query
        Map<UUID, ShortUrl> shortUrlMap =
                shortUrlRepository.findAllByIdIn(shortUrlIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ShortUrl::getId,
                                Function.identity()
                        ));


        // Convert valid requests into ClickEvent entities
        List<ClickEvent> clickEventsBatch = new ArrayList<>();
        List<MapRecord<String, Object, Object>> ackBatch =
                new ArrayList<>();

        for (ParsedAnalyticsRecord parsedRecord : parsedRecords) {

            ClickEventRequest request = parsedRecord.request();
            MapRecord<String, Object, Object> record =
                    parsedRecord.record();

            ShortUrl shortUrl =
                    shortUrlMap.get(request.shortUrlId());

            if (shortUrl == null) {

                log.warn(
                        "[ANALYTICS] Short URL not found for id={}",
                        request.shortUrlId()
                );

                acknowledgeAndDelete(record);
                continue;
            }

            try {

                ClickEvent clickEvent =
                        ClickEventMapper.toEntity(
                                request,
                                shortUrl
                        );

                clickEventsBatch.add(clickEvent);
                ackBatch.add(record);

            } catch (Exception ex) {

                metrics.recordAnalyticsProcessFailure();

                log.error(
                        "[ANALYTICS] Failed processing event recordId={}",
                        record.getId(),
                        ex
                );

                handleFailure(record, ex);
            }
        }

        // Step 4: Save all valid events together for better DB performance
        if (!clickEventsBatch.isEmpty()) {

            try {

                List<ClickEvent> savedEvents =
                        clickEventRepository.saveAll(
                                clickEventsBatch
                        );

                for (MapRecord<String, Object, Object> record :
                        ackBatch) {
                    acknowledgeAndDelete(record);
                }

                metrics.recordAnalyticsProcessSuccess(savedEvents.size());

                log.info(
                        "[ANALYTICS] Successfully processed {} events",
                        savedEvents.size()
                );

            } catch (Exception ex) {

                log.error(
                        "[ANALYTICS] Batch save failed, falling back to individual saves",
                        ex
                );

                // Individual save when batch save failed
                for (int i = 0; i < clickEventsBatch.size(); i++) {

                    try {

                        clickEventRepository.save(
                                clickEventsBatch.get(i)
                        );

                        acknowledgeAndDelete(
                                ackBatch.get(i)
                        );

                    } catch (Exception individualEx) {

                        metrics.recordAnalyticsProcessFailure();

                        handleFailure(
                                ackBatch.get(i),
                                individualEx
                        );
                    }
                }
            }
        }
    }

    /**
     * Marks a Redis stream record as successfully processed
     * and removes it from the stream.
     *
     * Why:
     * - ACK removes from consumer pending state
     * - DELETE prevents stream from growing forever
     */
    private boolean acknowledgeAndDelete(
            MapRecord<String, Object, Object> record
    ) {

        try {

            Long acked = redisTemplate.opsForStream().acknowledge(
                    GROUP_NAME,
                    record
            );

            Long deleted = redisTemplate.opsForStream().delete(
                    STREAM_KEY,
                    record.getId()
            );

            return acked != null && acked > 0 && deleted != null && deleted > 0;

        } catch (Exception ex) {

            log.error(
                    "[ANALYTICS] Failed ACK/DELETE recordId={}",
                    record.getId(),
                    ex
            );
        }

        return false;
    }

    /**
     * Handles failed event processing.
     *
     * Retry flow:
     * - Increment retry count
     * - Retry until max attempts reached
     * - Move permanently failing records to DLQ
     *
     * This prevents infinite retry loops.
     */
    private void handleFailure(
            MapRecord<String, Object, Object> record,
            Exception ex
    ) {

        metrics.recordAnalyticsRetry();

        String retryKey = RETRY_COUNT_PREFIX + record.getId();

        try {

            Long retryCount =
                    redisTemplate.opsForValue().increment(retryKey);


            if (retryCount == null) {
                log.error("[ANALYTICS] Failed retry count increment for recordId={}",
                        record.getId());
                return;
            }

            if (retryCount == 1) {
                redisTemplate.expire(retryKey, RETRY_TTL);
            }

            if (retryCount >= MAX_RETRY_ATTEMPTS) {

                metrics.recordAnalyticsRetryExhausted();

                log.error(
                        "[ANALYTICS] Max retries exceeded for recordId={}, moving to DLQ",
                        record.getId()
                );

                boolean dlqSuccess = moveToDeadLetterQueue(record, retryCount, ex);

                if (dlqSuccess) {

                    metrics.recordAnalyticsDlq();

                    boolean removed = acknowledgeAndDelete(record);
                    if (removed) {
                        redisTemplate.delete(retryKey);
                    }else {
                        log.error("[ANALYTICS] DLQ write succeeded but ACK/DELETE failed for recordId={}", record.getId());
                    }
                }

            } else {

                log.warn(
                        "[ANALYTICS] Retry attempt {} for recordId={}",
                        retryCount,
                        record.getId()
                );

                // Leave Pending
            }

        } catch (Exception retryEx) {

            log.error(
                    "[ANALYTICS] Retry handler failed for recordId={}",
                    record.getId(),
                    retryEx
            );
        }
    }


    /**
     * Moves permanently failed records to Dead Letter Queue (DLQ).
     *
     * DLQ keeps failed events for:
     * - Debugging
     * - Manual inspection
     * - Replay if needed
     */
    private boolean moveToDeadLetterQueue(
            MapRecord<String, Object, Object> record,
            Long retryCount,
            Exception failureReason
    ) {

        try {

            Map<String, String> dlqPayload = new HashMap<>();

            record.getValue().forEach((k, v) ->
                    dlqPayload.put(
                            String.valueOf(k),
                            String.valueOf(v)
                    )
            );

            dlqPayload.put("originalRecordId", record.getId().getValue());
            dlqPayload.put("retryCount", String.valueOf(retryCount));
            dlqPayload.put("failureReason",
                    failureReason.getClass().getSimpleName());

            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(DLQ_STREAM_KEY)
                            .ofMap(dlqPayload)
            );

            return true;

        } catch (Exception ex) {

            log.error(
                    "[ANALYTICS] Failed moving recordId={} to DLQ",
                    record.getId(),
                    ex
            );
        }

        return false;
    }
}
