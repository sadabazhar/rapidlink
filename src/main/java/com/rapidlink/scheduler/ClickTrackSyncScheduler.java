package com.rapidlink.scheduler;

import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ClickTrackingRepository;
import com.rapidlink.services.ClickTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Scheduler responsible for syncing click counts from Redis to Database.
 *
 * WHY THIS EXISTS:
 * - Avoids writing to DB on every user click (high load, slow)
 * - Redis stores click counts temporarily (fast, in-memory)
 * - This scheduler periodically flushes those counts into DB
 *
 * HOW IT WORKS:
 * 1. Runs every 30 seconds (fixedDelay)
 * 2. Fetches click counts from Redis (and resets them)
 * 3. Sends data to repository for batch DB update
 * 4. Logs success/failure
 *
 * IMPORTANT:
 * - This runs asynchronously in background (no user request involved)
 * - Acts as a bridge between Redis (fast layer) and DB (persistent layer)
 * - If this fails → click data may be lost
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClickTrackSyncScheduler {

    private final ClickTrackingService clickTrackingService;
    private final ClickTrackingRepository clickTrackingRepository;
    private final RapidLinkMetrics metrics;

    /**
     * Scheduled method that runs periodically to sync click counts.
     *
     * fixedDelay = 30000:
     * - Runs every 30 seconds AFTER previous execution completes
     * - Prevents overlapping executions (safer for DB operations)
     */
    @Scheduled(fixedDelay = 30000)
    public void syncClicks() {

        log.info("[SYNC] Starting click sync");

        // Step 1: Fetch click counts from Redis and reset them
        // Example result:
        // {
        //   "abc123": 120,
        //   "xyz789": 45
        // }
        Map<String, Long> counts = clickTrackingService.fetchAndReset();

        // Step 2: If no clicks found, skip DB operation
        if (counts.isEmpty()) {
            log.info("[SYNC] No clicks to sync");
            return;
        }

        try {
            // Step 3: Batch update DB
            // Internally:
            // - Uses JDBC batch update
            // - Efficiently updates multiple rows in one go
            // - Reduces DB load significantly

            // Total time taken to flush click counts to Database
            metrics.timeClickFlush(() -> {
                clickTrackingRepository.batchIncrement(counts);
                return null;
            });

            // Total clicks flushed
            long totalClicks = counts.values().stream().mapToLong(Long::longValue).sum();
            metrics.recordClickFlushToDb(totalClicks);
        } catch (Exception ex) {

            // Step 4: Handle failure
            //
            // IMPORTANT:
            // - If this fails, Redis data is already cleared (data loss risk)
            // - Should add retry or backup mechanism in production
            log.error("[SYNC] Batch update failed", ex);

            metrics.recordClickFlushFailure();
        }

        log.info("[SYNC] Completed. Synced {} keys", counts.size());
    }
}
