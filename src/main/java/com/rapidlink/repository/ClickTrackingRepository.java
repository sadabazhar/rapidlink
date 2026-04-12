package com.rapidlink.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository responsible for batching click count updates to the database.
 *
 * WHY THIS EXISTS:
 * - Avoids updating DB on every user click (which is slow and not scalable)
 * - Instead, Redis stores click counts temporarily
 * - This class flushes aggregated counts into DB in batches
 *
 * HOW IT WORKS:
 * - Accepts a map of shortCode → clickCount
 * - Uses JDBC batch update to efficiently update multiple rows
 * - Splits into smaller chunks (batchSize) to avoid DB overload
 *
 * IMPORTANT:
 * - This runs inside a scheduler (async background job)
 * - Ensures DB writes are minimized and efficient
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ClickTrackingRepository {

    // Spring JDBC helper for executing SQL queries efficiently
    private final JdbcTemplate jdbcTemplate;

    /**
     * Batch increment click counts for multiple short URLs.
     * @param counts Map of shortCode → number of clicks to increment
     * Example:
     * {
     *   "abc123": 120,
     *   "xyz789": 45
     * }
     */
    @Transactional
    public void batchIncrement(Map<String, Long> counts) {

        // SQL query to increment click count atomically in DB
        // Updates click count for any matching short_code
        String sql = """
            UPDATE short_urls
            SET click_count = click_count + ?
            WHERE short_code = ?
        """;

        // Convert Map → List<Object[]>, bcz of Order matters: ? → count ? → short_code
        // Example:
        // ["abc123", 120] → becomes [120, "abc123"]
        List<Object[]> batchArgs = counts.entrySet().stream()
                .map(e -> new Object[]{e.getValue(), e.getKey()})
                .toList();

        // Update only 500 shorturl click at a batch, Prevents sending too many updates in a single DB call
        int batchSize = 500;

        // Convert to modifiable list
        List<Object[]> args = new ArrayList<>(batchArgs);

        // Process updates in chunks
        // Example:
        // If 1200 records:
        // → 500 + 500 + 200 batches
        for (int i = 0; i < args.size(); i += batchSize) {
            List<Object[]> batch = args.subList(i, Math.min(i + batchSize, args.size()));

            // Each element represents rows affected (1 = success, 0 = no row found)
            int[] updated = jdbcTemplate.batchUpdate(sql, batch);

            // Loop through only not updated clicks
            for (int j = 0; j < updated.length; j++) {
                if (updated[j] == 0) {
                    String shortCode = (String) batch.get(j)[1];

                    log.warn("ShortCode not found during click flush: {}", shortCode);

                    // Todo: Move not updated click counts to retry queue
                }
            }
        }
    }
}
