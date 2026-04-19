package com.rapidlink.services.impl;

import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.InvalidStoredUrlException;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.exception.UrlDeactivatedException;
import com.rapidlink.exception.UrlExpiredException;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.ClickTrackingService;
import com.rapidlink.services.UrlCacheService;
import com.rapidlink.services.UrlRedirectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class UrlRedirectServiceImpl implements UrlRedirectService {

    private final RapidLinkMetrics metrics;
    private final ShortUrlRepository repository;
    private final UrlCacheService cacheService;
    private final ClickTrackingService clickService;

    private static final long MIN_CACHE_TTL_SECONDS = 5;


    // Resolves short code with original URL and tracks click
    @Override
    public URI getRedirectUrl(String shortCode) {


        // timeRedirect() wraps the ENTIRE resolution flow.
        return metrics.timeRedirect(() -> {

            URI result;

            // Cache check
            Optional<String> cached = cacheService.get(shortCode);
            if (cached.isPresent()) {

                // Negative cache HIT — this code was confirmed non-existent.
                // Skip DB entirely and return 404 immediately.
                if (cacheService.isNotFoundSentinel(cached.get())) {

                    // Even shortcode exists in negative cache, still count as not found.
                    metrics.recordRedirectNotFound();

                    log.info("Negative cache HIT — shortCode={}, returning 404 without DB query", shortCode);
                    throw new ShortUrlNotFoundException("Short URL not found, with this shortcode: " + shortCode);
                }

                log.info("Cache HIT — shortCode={}", shortCode);
                result = getFromCacheOrFallback(shortCode, cached.get());
            }else {

                //  If cache miss, DB lookup
                log.info("Cache MISS — shortCode={}, querying DB", shortCode);
                result = getFromDatabase(shortCode);
            }

            // Increment Redirect success at the end
            metrics.recordRedirectSuccess();
            return result;
        });
    }


    // -------- Helper Methods --------

    /**
     * Resolves a URL from cache and tracks the click.
     * Falls back to DB if the cached entry is stale (URL deactivated/expired since caching).
     * Evicts and throws if the cached URL itself is malformed.
     */
    private URI getFromCacheOrFallback(String shortCode, String cachedUrl){
        try {
            URI uri = URI.create(cachedUrl);

            // Atomic click increment in Redis
            clickService.increment(shortCode);

            return uri;

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
    private URI getFromDatabase(String shortCode){

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

        // Atomic click increment in Redis
        clickService.increment(shortCode);

        cacheWithAlignedTtl(shortCode, url);
        log.info("Redirecting — shortCode={}", shortCode);
        return uri;
    }

    /**
     * Caches the URL with a TTL aligned to its expiry:
     *   - URL has no expiry  → cache with default 24h TTL
     *   - URL expires soon   → cache only if remaining TTL > MIN_CACHE_TTL_SECONDS
     *   - URL nearly expired → skip caching to avoid serving stale data
     */
    private void cacheWithAlignedTtl(String shortCode, ShortUrl url) {
        if (url.getExpiresAt() == null) {
            cacheService.save(shortCode, url.getOriginalUrl());
            return;
        }

        Duration remainingTtl = Duration.between(LocalDateTime.now(), url.getExpiresAt());
        if (!remainingTtl.isNegative() && remainingTtl.getSeconds() > MIN_CACHE_TTL_SECONDS) {
            cacheService.save(shortCode, url.getOriginalUrl(), remainingTtl);
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

    // Validates that a short URL is active and not expired.
    private void validateUrl(ShortUrl url) {

        if (!url.getIsActive()) {
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
        validateUrl(url);
        return url;
    }
}
