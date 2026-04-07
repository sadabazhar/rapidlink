package com.rapidlink.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Central metrics component for RapidLink.
 *
 * This class is responsible for:
 * - Defining and registering all application metrics (Counters, Timers, Gauges)
 * - Providing a simple API for the service layer to record events
 *
 * Design principles:
 * - Metrics are created once at startup (@PostConstruct)
 * - No business logic lives here (only recording signals)
 * - Lightweight methods to avoid impacting request performance
 *
 * All metrics are exposed via Micrometer and scraped by Prometheus.
 */

@Component
@RequiredArgsConstructor
public class RapidLinkMetrics {

    /*
     * Central registry where all metrics are stored.
     * Prometheus reads metrics from here via /actuator/prometheus.
     */
    private final MeterRegistry registry;

    // ── Counters (event tracking) ────────────────────────────────────────────
    // Counters only increase; used for rates in Prometheus

    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Counter negativeCacheHitCounter;

    private Counter urlCreateSuccessCounter;
    private Counter urlCreateFailureCounter;

    private Counter redirectSuccessCounter;
    private Counter redirectNotFoundCounter;
    private Counter redirectExpiredCounter;

    private Counter clickIncrementCounter;
    private Counter clickFlushToDbCounter;

    // ── Timers (latency tracking) ────────────────────────────────────────────
    // Automatically tracks count, total time, and percentiles

    private Timer redirectLatencyTimer;
    private Timer urlCreateLatencyTimer;

    // ── Gauge (current state) ────────────────────────────────────────────────
    // Holds latest value; read during Prometheus scrape

    private final AtomicLong activeUrlCount = new AtomicLong(0);

    // ── Registration ──────────────────────────────────────────────────────────

    /*
     * @PostConstruct runs once after Spring injects all dependencies.
     * This is the right place to register metrics because:
     * - MeterRegistry is fully initialized by this point
     * - Metrics are registered once and reused (not recreated per request)
     * - Bcz, Recreating a Counter on every request would throw an exception
     */

    @PostConstruct
    public void registerMetrics() {

        // ── Cache counters ────────────────────────────────────────────────────

        /*
         * Metric naming uses dot notation.
         * Micrometer converts it to Prometheus format automatically.
         * Example: rapidlink.cache.hit → rapidlink_cache_hit_total
         */

        cacheHitCounter = Counter.builder("rapidlink.cache.hit")
                .description("Short URL was found in Redis — no DB query needed")
                .register(registry);

        cacheMissCounter = Counter.builder("rapidlink.cache.miss")
                .description("Short URL was not in Redis — DB fallback executed")
                .register(registry);

        negativeCacheHitCounter = Counter.builder("rapidlink.cache.negative.hit")
                .description("Redis held a sentinel value confirming short code does not exist — DB query skipped")
                .register(registry);

        // ── URL creation and Redirect counters ─────────────────────────────────────────

        /*
         * Using separate counters instead of tags for simplicity.
         * Easier to integrate and query.
         * Can be refactored to tagged metrics later if needed.
         */

        urlCreateSuccessCounter = Counter.builder("rapidlink.url.create.success")
                .description("Short URL created and persisted successfully")
                .register(registry);

        urlCreateFailureCounter = Counter.builder("rapidlink.url.create.failure")
                .description("Short URL creation failed — validation error, DB error, or duplicate")
                .register(registry);

        // Redirect tracking
        redirectSuccessCounter = Counter.builder("rapidlink.redirect.success")
                .description("Short URL resolved and redirect returned successfully")
                .register(registry);

        redirectNotFoundCounter = Counter.builder("rapidlink.redirect.not_found")
                .description("Short code did not exist in cache or DB")
                .register(registry);

        redirectExpiredCounter = Counter.builder("rapidlink.redirect.expired")
                .description("Short URL existed but its expiry time has passed")
                .register(registry);

        // Click tracking
        clickIncrementCounter = Counter.builder("rapidlink.click.increment")
                .description("A click was recorded — either directly to DB (before) or to Redis (after optimization)")
                .register(registry);

        clickFlushToDbCounter = Counter.builder("rapidlink.click.flush.db")
                .description("Clicks flushed from Redis to PostgreSQL in a batch write")
                .register(registry);


        // ── Create and Redirect latency timer ────────────────────────────────────────────

        /*
         * Pre-computes common percentiles (p50, p95, p99)
         * so they are easier to use in dashboards.
         */

        urlCreateLatencyTimer = Timer.builder("rapidlink.url.create.latency")
                .description("Full duration of URL creation: validation → code generation → DB insert → cache write")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry);

        redirectLatencyTimer = Timer.builder("rapidlink.redirect.latency")
                .description("Full duration of short URL resolution: cache check → optional DB query → expiry check")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry);

        // ── Active URL gauge ──────────────────────────────────────────────────

        /*
         * Gauge reads the current value of activeUrlCount during each scrape.
         * The value is controlled manually via setActiveUrlCount().
         */

        Gauge.builder("rapidlink.urls.active", activeUrlCount, AtomicLong::get)
                .description("Current count of non-expired shortened URLs")
                .register(registry);

    }

    // ── Public API ────────────────────────────────────────────────────────────

    /*
     * Methods used by the service layer to record metrics.
     * Each method is intentionally simple (one-liner).
     * This keeps metrics lightweight and safe to use in critical paths.
     */

    // Cache
    public void recordCacheHit()  { cacheHitCounter.increment(); }
    public void recordCacheMiss() { cacheMissCounter.increment(); }
    public void recordNegativeCacheHit() { negativeCacheHitCounter.increment(); }

    // URL creation
    public void recordUrlCreateSuccess() { urlCreateSuccessCounter.increment(); }
    public void recordUrlCreateFailure() { urlCreateFailureCounter.increment(); }

    // Redirect outcomes
    public void recordRedirectSuccess()  { redirectSuccessCounter.increment(); }
    public void recordRedirectNotFound() { redirectNotFoundCounter.increment(); }
    public void recordRedirectExpired()  { redirectExpiredCounter.increment(); }

    // Click tracking
    public void recordClickIncrement()          { clickIncrementCounter.increment(); }
    public void recordClickFlushToDb(long count) { clickFlushToDbCounter.increment(count); }

    // Error
    /*
     * Records errors with a dynamic "type" tag (e.g., db, redis, validation).
     * A new metric series is created per type.
     */
    public void recordError(String type) {
        Counter.builder("rapidlink.error")
                .description("Unexpected system error")
                .tag("type", type)
                .register(registry)
                .increment();
    }

    // Timer
    /*
     * Wraps any operation and records its execution time.
     * Ensures latency is recorded even if the operation throws an exception.
     */

    public <T> T timeUrlCreate(Supplier<T> operation) {
        return urlCreateLatencyTimer.record(operation);
    }

    public <T> T timeRedirect(Supplier<T> operation) {
        return redirectLatencyTimer.record(operation);
    }

    // Gauge
    // Update active URL count when URLs are created or expired
    public void setActiveUrlCount(long count) { activeUrlCount.set(count); }

}
