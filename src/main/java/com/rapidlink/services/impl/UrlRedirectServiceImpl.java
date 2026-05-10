package com.rapidlink.services.impl;

import com.rapidlink.dto.cache.CacheResult;
import com.rapidlink.dto.cache.CachedShortUrl;
import com.rapidlink.dto.internal.RedirectResolution;
import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.InvalidStoredUrlException;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.exception.UrlDeactivatedException;
import com.rapidlink.exception.UrlExpiredException;
import com.rapidlink.mapper.CachedShortUrlMapper;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
class UrlRedirectServiceImpl implements UrlRedirectService {

    private final RapidLinkMetrics metrics;
    private final ShortUrlRepository repository;
    private final UrlCacheService cacheService;
    private final ClickTrackingService clickService;
    private final AnalyticsEventProducer analyticsEventProducer;
    private final ClickMetadataExtractor clickMetadataExtractor;

    private static final long MIN_CACHE_TTL_SECONDS = 5;


    // Resolves short code with original URL and tracks click
    @Override
    public URI getRedirectUrl(String shortCode, HttpServletRequest request) {


        // timeRedirect() wraps the ENTIRE resolution flow.
        return metrics.timeRedirect(() -> {

            RedirectResolution result;

            // Cache check
            CacheResult cacheResult = cacheService.get(shortCode);

            switch (cacheResult.type()){

                // Negative cache HIT — Skip DB entirely and return 404 immediately.
                case NEGATIVE_HIT -> {
                    // Even shortcode exists in negative cache, still count as not found.
                    metrics.recordRedirectNotFound();

                    log.info("Negative cache HIT — shortCode={}, returning 404 without DB query", shortCode);
                    throw new ShortUrlNotFoundException("Short URL not found, with this shortcode: " + shortCode);
                }

                // Cache HIT — fetch the value from cache
                case HIT -> {
                    log.info("Cache HIT — shortCode={}", shortCode);
                    result = getFromCacheOrFallback(shortCode, cacheResult.value());
                }

                // Cache MISS — DB lookup
                case MISS -> {
                    log.info("Cache MISS — shortCode={}, querying DB", shortCode);
                    result = getFromDatabase(shortCode);
                }

                default -> {
                    log.error("Unexpected cache result type — shortCode={}, type={}", shortCode, cacheResult.type());
                    throw new IllegalStateException(
                            "Unexpected cache result type: " + cacheResult.type()
                    );
                }
            }

            // Click event should not fail redirect
            try{
                // Extract the metadata from request
                ClickEventRequest clickEvent = clickMetadataExtractor.extract(result.shortUrlId(), request);

                // Publish the event into redis stream
                analyticsEventProducer.publish(clickEvent);
            }catch (Exception ex){
                log.error("Analytics pipeline failure", ex);
            }

            // Atomic click increment in Redis
            clickService.increment(shortCode);

            // Increment Redirect success at the end
            metrics.recordRedirectSuccess();
            return result.uri();
        });
    }


    // -------- Helper Methods --------

    /**
     * Resolves a URL from cache and tracks the click.
     * Falls back to DB if the cached entry is stale (URL deactivated/expired since caching).
     * Evicts and throws if the cached URL itself is malformed.
     */
    private RedirectResolution getFromCacheOrFallback(String shortCode, CachedShortUrl cachedUrl){
        try {

            validateCachedUrl(cachedUrl);

            URI uri = parseUri(cachedUrl.originalUrl(), cachedUrl.shortCode());

            return RedirectResolution.builder()
                    .shortUrlId(cachedUrl.id())
                    .uri(uri)
                    .build();

            // evict and fall back to DB, user gets their redirect
        } catch (IllegalArgumentException ex) {
            log.error("Corrupted URL in cache, evicting and retrying from DB — shortCode={}", shortCode);
            cacheService.delete(shortCode);
            return getFromDatabase(shortCode);  // DB is source of truth
        }
    }

    /**
     * Fetches the original URL from DB and prepares it for redirect.
     * Handles the race condition where a URL is deactivated between validation and click tracking.
     * Acts as the source of truth — DB is always trusted over cache.
     */
    private RedirectResolution getFromDatabase(String shortCode){

        // findAndValidate throws ShortUrlNotFoundException if the code doesn't exist.
        // We intercept that specific case to write the negative sentinel BEFORE rethrowing.
        ShortUrl url;

        try {
            url = findAndValidate(shortCode);
        } catch (ShortUrlNotFoundException ex) {

            // DB confirmed: this short code does not exist. or exits but expired
            // recordRedirectNotFound() fires here
            metrics.recordRedirectNotFound();

            // Cache the negative result so future requests skip the DB entirely.
            cacheService.saveNotFound(shortCode);
            log.info("Negative cache written — shortCode={}", shortCode);
            throw ex; // rethrow — caller (resolveUrl) handles the 404 response
        }catch (UrlDeactivatedException ex){

            // URL was manually deactivated. Counted as not_found from the user's perspective
            metrics.recordRedirectNotFound();
            throw ex;
        }catch (UrlExpiredException ex){

            // URL exists in DB but has passed its expiry time.
            metrics.recordRedirectExpired();
            throw ex;
        }

        // Build URI FIRST (avoid caching invalid URL)
        URI uri = parseUri(url.getOriginalUrl(), shortCode);

        // cache the short url obj
        CachedShortUrl cachedShortUrl = CachedShortUrlMapper.toCachedUrl(url);

        cacheWithAlignedTtl(shortCode, cachedShortUrl);
        log.info("Redirecting — shortCode={}", shortCode);
        return RedirectResolution.builder()
                .shortUrlId(url.getId())
                .uri(uri)
                .build();
    }

    /**
     * Caches the URL with a TTL aligned to its expiry:
     *   - URL has no expiry  → cache with default 24h TTL
     *   - URL expires soon   → cache only if remaining TTL > MIN_CACHE_TTL_SECONDS
     *   - URL nearly expired → skip caching to avoid serving stale data
     */
    private void cacheWithAlignedTtl(String shortCode, CachedShortUrl cachedShortUrl) {
        if (cachedShortUrl.expiresAt() == null) {
            cacheService.save(shortCode, cachedShortUrl);
            return;
        }

        Duration remainingTtl = Duration.between(LocalDateTime.now(), cachedShortUrl.expiresAt());
        if (!remainingTtl.isNegative() && remainingTtl.getSeconds() > MIN_CACHE_TTL_SECONDS) {
            cacheService.save(shortCode, cachedShortUrl, remainingTtl);
        } else {
            log.debug("Skipping cache — TTL too short — shortCode={}, remainingTtl={}s",
                    shortCode, remainingTtl.getSeconds());
        }
    }

    // Wraps URI.create() with a meaningful exception on malformed stored URLs
    private URI parseUri(String rawUrl, String shortCode) {
        try {
            return URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid URL stored in DB — shortCode={}", shortCode);
            throw new InvalidStoredUrlException(shortCode);
        }
    }

    // Validates that a cached short URL is active and not expired.
    private void validateCachedUrl(CachedShortUrl url) {

        if (Boolean.FALSE.equals(url.isActive())) {

            cacheService.delete(url.shortCode());
            log.warn("Attempt to access deactivated cached URL: shortCode={}", url.shortCode());
            metrics.recordRedirectNotFound();
            throw new UrlDeactivatedException("Short URL is deactivated, with this shortcode : " + url.shortCode());
        }

        if (url.expiresAt() != null &&
                url.expiresAt().isBefore(LocalDateTime.now())) {

            cacheService.delete(url.shortCode());
            log.warn("Attempt to access expired cached URL: shortCode={}", url.shortCode());
            metrics.recordRedirectExpired();
            throw new UrlExpiredException("Short URL is expired, with this shortcode : " + url.shortCode());
        }
    }

    // Validates that a short URL is active and not expired.
    private void validateDatabaseUrl(ShortUrl url) {

        if (Boolean.FALSE.equals(url.getIsActive())) {
            log.warn("Attempt to access deactivated URL: shortCode={}", url.getShortCode());
            throw new UrlDeactivatedException("Short URL is deactivated, with this shortcode : " + url.getShortCode());
        }

        if (url.getExpiresAt() != null &&
                url.getExpiresAt().isBefore(LocalDateTime.now())) {

            log.warn("Attempt to access expired URL: shortCode={}", url.getShortCode());
            throw new UrlExpiredException("Short URL is expired, with this shortcode : " + url.getShortCode());
        }
    }

    // Fetches a ShortUrl by shortCode and immediately validates it.
    private ShortUrl findAndValidate(String shortCode) {
        ShortUrl url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.warn("Short URL not found — shortCode={}", shortCode);
                    return new ShortUrlNotFoundException("Short URL not found, with this shortcode : " + shortCode);
                });
        validateDatabaseUrl(url);
        return url;
    }
}