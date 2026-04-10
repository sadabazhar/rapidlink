package com.rapidlink.services.impl;

import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.services.ClickTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for tracking click counts using Redis.
 *
 * WHY THIS EXISTS:
 * - Avoids updating database on every user click (which is slow)
 * - Uses Redis as a fast, in-memory counter store
 * - Scheduler later flushes aggregated counts into DB
 *
 * IMPORTANT:
 * - Redis acts as temporary buffer
 * - DB is updated asynchronously via scheduler
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClickTrackingServiceImpl implements ClickTrackingService {

    private final StringRedisTemplate redisTemplate;
    private final RapidLinkMetrics metrics;

    private static final String PREFIX = "click_count:";
    private static final String ACTIVE_KEYS_SET = "active_click_keys";

    /**
     * Increment click count for a given shortCode.
     * Also tracks active keys using Redis Set.
     * Called on every redirect request.
     */
    @Override
    public void increment(String shortCode) {

        try {

            // If key doesn't exist → created with value 1
            // If exists → incremented atomically
            redisTemplate.opsForValue().increment(PREFIX + shortCode);

            // Track active key (only once, Set avoids duplicates)
            redisTemplate.opsForSet().add(ACTIVE_KEYS_SET, shortCode);

            // Increment click count
            metrics.recordClickIncrement();
        } catch (Exception ex) {
            log.warn("Redis INCR failed for shortCode={}, skipping click increment", shortCode);

            // // Increment click failure
            metrics.recordClickIncrementFailure();
        }
    }

    /**
     * Fetch all click counts from Redis and reset them.
     *
     * Called by scheduler periodically.
     *
     * Steps:
     * 1. Get all active shortCodes from Redis Set
     * 2. Read click count for each
     * 3. Store in result map
     * 4. Delete Redis key (reset counter)
     * 5. Remove key from active set
     */
    @Override
    public Map<String, Long> fetchAndReset() {

        // Total time taken to fetch click counts from redis
        return metrics.timeClickFetch(() -> {
            // Stores result: shortCode → click count
            Map<String, Long> counts = new HashMap<>();

            try {
                // 1. Get only active keys
                Set<String> activeKeys = redisTemplate.opsForSet().members(ACTIVE_KEYS_SET);

                if (activeKeys == null || activeKeys.isEmpty()) {
                    return counts;
                }

                // 2. Process each active key
                for (String shortCode : activeKeys) {

                    String key = PREFIX + shortCode;

                    try {
                        // 3. Get and delete click count
                        String value = redisTemplate.opsForValue().getAndDelete(key);

                        if (value == null || value.equals("0")) {
                            // Cleanup stale key
                            redisTemplate.opsForSet().remove(ACTIVE_KEYS_SET, shortCode);
                            continue;
                        }

                        // 4. Store result
                        counts.put(shortCode, Long.parseLong(value));

                        // 5. Remove from active set
                        redisTemplate.opsForSet().remove(ACTIVE_KEYS_SET, shortCode);

                    } catch (Exception ex) {
                        log.error("Failed processing shortCode={}", shortCode, ex);
                    }
                }

                // Track how many keys processed
                metrics.recordClickFetch(counts.size());

            } catch (Exception ex) {
                log.error("Failed fetching active click keys", ex);

                metrics.recordClickFetchFailure();
            }

            return counts;
        });
    }
}
