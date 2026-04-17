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
 * - Writing to DB on every click is slow and not scalable
 * - Click counts are first stored in Redis (fast, in-memory)
 * - This class periodically flushes aggregated counts into DB
 *
 * HOW IT WORKS:
 * - Accepts a map of shortCode → clickCount
 * - Uses JDBC batch update to update multiple rows efficiently
 * - Splits updates into smaller chunks to avoid DB overload
 *
 * FAILURE HANDLING:
 * - If any shortCode is not found in DB → fail fast
 * - Throws exception to signal scheduler to retry the whole batch
 * - Keeps logic simple (all success OR all retry)
 *
 * IMPORTANT:
 * - Called by scheduler (background job, not user request)
 * - Uses atomic DB update (click_count = click_count + ?)
 * - Safe for retries (no duplicate issues due to incremental update)
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

        // SQL query to increment click count atomically
        // Example:
        // click_count = click_count + 120
        String sql = """
            UPDATE short_urls
            SET click_count = click_count + ?
            WHERE short_code = ?
        """;

        // Convert Map → List<Object[]>
        // Bcz, order matters: first "count", then "short_code"
        // Example:
        // ("abc123", 120) → [120, "abc123"]
        List<Object[]> batchArgs = counts.entrySet().stream()
                .map(e -> new Object[]{e.getValue(), e.getKey()})
                .toList();

        // Update only 500 shorturl click at a batch, Prevents sending too many updates in a single DB call
        int batchSize = 500;

        // Convert to modifiable list (subList needs this)
        List<Object[]> args = new ArrayList<>(batchArgs);

        // Process updates in chunks
        // Example:
        // If 1200 records:
        // → 500 + 500 + 200 batches
        for (int i = 0; i < args.size(); i += batchSize) {

            // Create sub-batch
            List<Object[]> batch = args.subList(i, Math.min(i + batchSize, args.size()));

            // Execute batch update
            // Each value in result:
            // 1 → row updated
            // 0 → no row found for that shortCode
            int[] updated = jdbcTemplate.batchUpdate(sql, batch);

            // Check for failures (rows not updated)
            for (int j = 0; j < updated.length; j++) {

                // If DB did not update row → shortCode missing
                if (updated[j] == 0) {

                    // Get failed shortCode from batch
                    String shortCode = (String) batch.get(j)[1];

                    log.warn("ShortCode not found during click flush: {}", shortCode);

                    // FAIL FAST → trigger retry for whole batch
                    throw new IllegalStateException("ShortCode not found: " + shortCode);

                }
            }
        }
    }
}
