package com.rapidlink.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.entity.ClickEvent;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.mapper.ClickEventMapper;
import com.rapidlink.repository.ClickEventRepository;
import com.rapidlink.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler responsible for consuming analytics click events
 * from Redis Streams and persisting them into PostgreSQL.
 *
 * WHY THIS EXISTS:
 * - Redirect path must remain ultra-fast
 * - Analytics DB writes should be async
 * - Redis Stream acts as durable event buffer
 * - Supports replay and fault tolerance
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumerScheduler {

    private static final String STREAM_KEY = "analytics:click_events";
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;

    /**
     * Runs every 30 seconds.
     *
     * Reads analytics events from Redis Stream,
     * persists them to DB,
     * and acknowledges successful processing.
     */
    @Scheduled(fixedDelay = 30000)
    public void processAnalyticsEvents() {

        log.info("[ANALYTICS] Starting analytics event sync");

        try {

            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(StreamReadOptions.empty()
                                    .count(BATCH_SIZE)
                                    .block(Duration.ofSeconds(1)),
                            StreamOffset.fromStart(STREAM_KEY));


            if (records == null || records.isEmpty()) {
                log.info("[ANALYTICS] No analytics events to process");
                return;
            }

            int processedCount = 0;

            for (MapRecord<String, Object, Object> record : records) {

                try {

                    Object payloadObj = record.getValue().get("payload");

                    if (payloadObj == null) {
                        continue;
                    }

                    String payload = payloadObj.toString();

                    ClickEventRequest request =
                            objectMapper.readValue(payload, ClickEventRequest.class);

                    Optional<ShortUrl> shortUrlOpt =
                            shortUrlRepository.findById(request.shortUrlId());

                    if (shortUrlOpt.isEmpty()) {
                        log.warn("[ANALYTICS] Short URL not found for id={}",
                                request.shortUrlId());

                        deleteProcessedRecord(record);
                        continue;
                    }

                    ShortUrl shortUrl = shortUrlOpt.get();

                    ClickEvent clickEvent =
                            ClickEventMapper.toEntity(request, shortUrl);

                    clickEventRepository.save(clickEvent);

                    processedCount++;

                    // Delete processed event from stream
                    deleteProcessedRecord(record);

                } catch (Exception ex) {
                    log.error("[ANALYTICS] Failed processing event recordId={}",
                            record.getId(), ex);
                }
            }

            log.info("[ANALYTICS] Successfully processed {} events",
                    processedCount);

        } catch (Exception ex) {
            log.error("[ANALYTICS] Stream processing failed", ex);
        }
    }

    /**
     * Deletes successfully processed stream records.
     */
    private void deleteProcessedRecord(MapRecord<String, Object, Object> record) {
        try {
            redisTemplate.opsForStream()
                    .delete(STREAM_KEY, record.getId());

        } catch (Exception ex) {
            log.error("[ANALYTICS] Failed deleting stream recordId={}",
                    record.getId(), ex);
        }
    }
}
