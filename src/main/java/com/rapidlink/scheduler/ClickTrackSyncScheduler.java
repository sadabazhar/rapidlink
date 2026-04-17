package com.rapidlink.scheduler;

import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ClickTrackingRepository;
import com.rapidlink.services.ClickTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scheduler responsible for syncing click counts from Redis to Database.
 *
 * WHY THIS EXISTS:
 * - Writing to DB on every click is slow and expensive
 * - Redis is used as a fast in-memory counter (temporary storage)
 * - This scheduler periodically moves aggregated counts to DB
 *
 * HOW IT WORKS:
 * 1. Runs every 30 seconds (fixedDelay)
 * 2. First processes FAILED (retry) data to avoid data loss
 * 3. Then processes fresh click data from Redis
 * 4. Writes all data to DB using batch update
 * 5. On success → deletes Redis keys
 * 6. On failure → moves data to retry queue
 *
 * RETRY + DLQ FLOW:
 * - Failed data is stored in "retry:" keys
 * - Retry count is tracked using "retry_count:" keys
 * - If retry exceeds limit → moved to DLQ ("dlq:")
 * - Prevents infinite retry loops
 *
 * LUA USAGE:
 * - Used for safe data movement (no race conditions)
 * - Ensures merge + delete happens atomically
 *
 * IMPORTANT:
 * - Runs in background (no user request)
 * - Ensures eventual consistency between Redis and DB
 * - Prevents data loss using retry mechanism
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClickTrackSyncScheduler {

    private final ClickTrackingService clickTrackingService;
    private final ClickTrackingRepository clickTrackingRepository;
    private final RapidLinkMetrics metrics;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> moveToRetryAtomicScript;
    private final DefaultRedisScript<Long> moveToDlqScript;

    private static final String PROCESSING_PREFIX = "processing:";
    private static final String RETRY_PREFIX = "retry:";
    private static final String RETRY_KEYS_SET = "retry_keys";

    private static final String RETRY_COUNT_PREFIX = "retry_count:";
    private static final int MAX_RETRY = 5;
    private static final String DLQ_PREFIX = "dlq:";

    /**
     * Main scheduler method
     * Runs every 30 seconds after previous execution completes
     */
    @Scheduled(fixedDelay = 30000)
    public void syncClicks() {

        log.info("[SYNC] Starting click sync");

        // STEP 1: Process retry data first (high priority to avoid data loss)
        processRetryKeys();

        // STEP 2: Fetch fresh click data and reset counters in Redis
        Map<String, Long> counts = clickTrackingService.fetchAndReset();

        // If no data → skip DB call
        if (counts.isEmpty()) {
            log.info("[SYNC] No new clicks to sync");
            return;
        }

        // Process fresh data (write to DB)
        processBatch(counts, PROCESSING_PREFIX);

        log.info("[SYNC] Completed. Synced {} keys", counts.size());
    }

    /**
     * Process retry data first.
     *
     * - Reads all retry keys from Redis Set
     * - Skips empty/invalid data
     * - Tracks retry count
     * - Moves data to DLQ if retry limit exceeded
     */
    private void processRetryKeys() {

        // Get all retry shortCodes from Redis Set
        Set<String> retryShortCodes = redisTemplate.opsForSet().members(RETRY_KEYS_SET);

        // If no retry data → skip
        if (retryShortCodes == null || retryShortCodes.isEmpty()) {
            return;
        }

        // Map to store retry data (shortCode → count)
        Map<String, Long> retryCounts = new HashMap<>();

        // Loop through each retry key
        for (String shortCode : retryShortCodes) {

            String retryCountKey = RETRY_COUNT_PREFIX + shortCode;
            String retryCountVal = redisTemplate.opsForValue().get(retryCountKey);
            long retryCount = (retryCountVal != null) ? Long.parseLong(retryCountVal) : 0;

            // If retry exceeded → move to DLQ
            if (retryCount > MAX_RETRY) {

                String retryKey = RETRY_PREFIX + shortCode;
                String dlqKey = DLQ_PREFIX + shortCode;

                // Move retry → DLQ (If key is already exists merge it)
                redisTemplate.execute(
                        moveToDlqScript,
                        List.of(retryKey, dlqKey)
                );

                // Cleanup tracking
                redisTemplate.opsForSet().remove(RETRY_KEYS_SET, shortCode);
                redisTemplate.delete(retryCountKey);

                log.error("[DLQ] Moved to DLQ after max retries: {}", shortCode);

                continue;
            }

            String retryKey = RETRY_PREFIX + shortCode;

            try {

                // Get stored click count
                String value = redisTemplate.opsForValue().get(retryKey);

                // If value is null or zero → cleanup
                if (value == null || value.equals("0")) {
                    redisTemplate.delete(retryKey);
                    redisTemplate.opsForSet().remove(RETRY_KEYS_SET, shortCode);
                    continue;
                }

                // Add to retry map
                retryCounts.put(shortCode, Long.parseLong(value));

            } catch (Exception ex) {
                log.error("[RETRY] Failed reading retry key for {}", shortCode, ex);
            }
        }

        log.info("[RETRY] Processing {} retry keys", retryCounts.size());

        // Process retry batch (same logic as normal batch)
        processBatch(retryCounts, RETRY_PREFIX);
    }

    /**
     * Batch write click counts to DB.
     *
     * - On success → deletes Redis keys
     * - On retry success → also clears retry metadata
     * - On failure → moves all data to retry queue
     */
    private void processBatch(Map<String, Long> counts, String prefix) {

        try {

            // Measure DB flush time
            metrics.timeClickFlush(() -> {
                clickTrackingRepository.batchIncrement(counts);
                return null;
            });

            long totalClicks = counts.values().stream().mapToLong(Long::longValue).sum();
            log.info("[SYNC] totalClicks={}, keys={}", totalClicks, counts.size());

            metrics.recordClickFlushToDb(totalClicks);

            // After successful DB update → delete Redis keys
            for (String shortCode : counts.keySet()) {

                // Delete processing/retry key
                redisTemplate.delete(prefix + shortCode);

                // If this was retry data → remove from retry set
                if (RETRY_PREFIX.equals(prefix)) {
                    redisTemplate.opsForSet().remove(RETRY_KEYS_SET, shortCode);

                    // Reset retry counter after success
                    redisTemplate.delete(RETRY_COUNT_PREFIX + shortCode);
                }
            }

        } catch (Exception ex) {

            log.error("[SYNC] Batch update failed completely", ex);

            // Move ALL data to retry queue (important for data safety)
            for (String shortCode : counts.keySet()) {

                if (PROCESSING_PREFIX.equals(prefix)) {
                    // only move fresh data to retry
                    moveToRetry(shortCode, prefix + shortCode);
                } else {
                    // already retry → just increase retry count
                    redisTemplate.opsForValue().increment(RETRY_COUNT_PREFIX + shortCode);
                }
            }

            metrics.recordClickFlushFailure();
        }
    }

    /**
     * Move failed data to retry queue using Lua (atomic).
     *
     * - Moves count from sourceKey → retryKey
     * - Merges if retryKey already exists
     * - Increments retry counter
     * - Tracks retry key for future processing
     *
     * Ensures no data loss during concurrent updates.
     */
    private void moveToRetry(String shortCode, String sourceKey) {

        String retryKey = RETRY_PREFIX + shortCode;
        String retryCountKey = RETRY_COUNT_PREFIX + shortCode;

        try {

            // Execute Lua script (Atomically performs):
            // (Read val from processing key, merge into retry key, delete processing key, incr retry, add to retry set)
            redisTemplate.execute(
                    moveToRetryAtomicScript,
                    List.of(
                            sourceKey,
                            retryKey,
                            retryCountKey,
                            RETRY_KEYS_SET
                    ),
                    shortCode
            );


            log.warn("[RETRY] Moved to retry queue: {}", shortCode);

        } catch (Exception ex) {
            log.error("[RETRY] Failed to move key to retry: {}", shortCode, ex);
        }
    }
}
