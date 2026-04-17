package com.rapidlink.services.impl;

import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.services.ClickTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for tracking click counts using Redis.
 *
 * WHY THIS EXISTS:
 * - Updating DB on every click is slow and not scalable
 * - Redis is used as a fast in-memory counter for clicks
 * - Clicks are aggregated in Redis and later flushed to DB by scheduler
 *
 * HOW IT WORKS:
 * - On each user click → increment Redis counter
 * - Track active shortCodes using a Redis Set
 * - Scheduler periodically fetches and resets counts
 *
 * DATA FLOW:
 * click_count:shortCode → processing:shortCode → DB → (success delete / failure retry)
 *
 * IMPORTANT:
 * - Redis acts as a temporary buffer (eventual consistency)
 * - Uses atomic operations to avoid race conditions
 * - Designed to work safely with scheduler + retry mechanism
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickTrackingServiceImpl implements ClickTrackingService {

    private final StringRedisTemplate redisTemplate;
    private final RapidLinkMetrics metrics;
    private final DefaultRedisScript<Long> incrementAndTrackScript;
    private final DefaultRedisScript<Long> moveToProcessingScript;

    private static final String CLICK_PREFIX = "click_count:";
    private static final String ACTIVE_KEYS_SET = "active_click_keys";
    private static final String PROCESSING_PREFIX = "processing:";

    /**
     * Increment click count for a given shortCode.
     * Called on every redirect request.
     */
    @Override
    public void increment(String shortCode) {

        try {

            // Execute Lua script (Atomic):
            // 1. INCR click_count:<shortCode>
            // 2. SADD active_click_keys <shortCode>
            redisTemplate.execute(
                    incrementAndTrackScript,
                    List.of(
                            CLICK_PREFIX + shortCode, // KEYS[1]
                            ACTIVE_KEYS_SET           // KEYS[2]
                    ),
                    shortCode // ARGV[1]
            );

            // Increment click count
            metrics.recordClickIncrement();
        } catch (Exception ex) {
            log.warn("Redis INCR failed for shortCode={}, skipping click increment", shortCode);

            // Increment click failure
            metrics.recordClickIncrementFailure();
        }
    }

    /**
     * Fetch all click counts from Redis and reset them.
     * Called by scheduler periodically.
     */
    @Override
    public Map<String, Long> fetchAndReset() {

        // Measure time taken for fetching data
        return metrics.timeClickFetch(() -> {

            // Map to store results (shortCode → click count)
            Map<String, Long> counts = new HashMap<>();

            try {
                // Get all active shortCodes from Redis Set
                Set<String> activeKeys = redisTemplate.opsForSet().members(ACTIVE_KEYS_SET);

                // If no active keys → return empty result
                if (activeKeys == null || activeKeys.isEmpty()) {
                    return counts;
                }

                // Process each shortCode
                for (String shortCode : activeKeys) {

                    String clickKey = CLICK_PREFIX + shortCode;
                    String processingKey = PROCESSING_PREFIX + shortCode;

                    try {

                        /*
                         * Atomic handoff using Lua:
                         * - Moves click_count → processing
                         * - Merges if processing already exists
                         * - Prevents overwrite and data loss
                         */

                        // Move click_count → processing safely using Lua
                        // This prevents overwrite and merges if processing key already exists
                        redisTemplate.execute(
                                moveToProcessingScript,
                                List.of(clickKey, processingKey)
                        );

                        // Get merged value from processing key
                        String value = redisTemplate.opsForValue().get(processingKey);

                        // If value is null or zero → cleanup and skip
                        if (value == null || value.equals("0")) {
                            redisTemplate.delete(processingKey);
                            continue;
                        }

                        // Store parsed value in result map
                        counts.put(shortCode, Long.parseLong(value));

                        // Remove from active set only if no new clicks happened
                        if (!Boolean.TRUE.equals(redisTemplate.hasKey(clickKey))) {
                            redisTemplate.opsForSet().remove(ACTIVE_KEYS_SET, shortCode);
                        }

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

            // Return collected counts
            return counts;
        });
    }
}
