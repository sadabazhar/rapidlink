package com.rapidlink.services.impl;

import com.rapidlink.encoder.Base62Encoder;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.*;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.ShortUrlService;
import com.rapidlink.services.UrlCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortUrlServiceImpl implements ShortUrlService {

    private final ShortUrlRepository repository;
    private final UrlCacheService cacheService;
    private static final long MIN_CACHE_TTL_SECONDS = 5;
    private static final int MAX_URL_LENGTH = 2048;

    // Creates a short URL for the given original URL
    @Override
    @Transactional
    public String createShortUrl(String originalUrl) {

        log.info("Creating short URL request received");

        URI validatedUri = validateAndNormalizeUrl(originalUrl);
        String normalizedUrl = validatedUri.toString();

        Long seqId = repository.nextSeqId();
        if (seqId == null) {
            throw new ShortCodeGenerationException("DB Sequence returned null");
        }

        // TODO: Current implementation is predictable (seq_id → Base62).
        // Consider adding obfuscation (e.g., hashing or ID scrambling)
        // to prevent enumeration attacks in public URLs.
        String shortCode = Base62Encoder.encode(seqId);

        ShortUrl url = ShortUrl.builder()
                .originalUrl(normalizedUrl)
                .seqId(seqId)
                .shortCode(shortCode)
                .isActive(true)
                .clickCount(0L)
                .build();

        try {
            // Force immediate DB flush so constraint violations are thrown here
            // (otherwise exceptions occur at transaction commit, outside this try-catch)
            repository.saveAndFlush(url);
        } catch (DataIntegrityViolationException ex) {
            log.error("DB constraint violation during short URL creation — shortCode={}", shortCode, ex);
            throw new ShortCodeGenerationException();
        }

        // Write-through cache warm — fires only AFTER @Transactional commits
        registerCacheAfterCommit(shortCode, normalizedUrl);

        log.info("Short URL created successfully: shortCode={}", shortCode);
        return shortCode;
    }

    // Resolves short code with original URL and tracks click
    @Override
    @Transactional // TODO: Temporary for MVP. Replace with Redis-based counter + async persistence to avoid transaction overhead and row-level contention under high traffic.
    public URI resolveUrl(String shortCode) {

        // Cache check
        Optional<String> cached = cacheService.get(shortCode);
        if (cached.isPresent()) {

            log.info("Cache HIT — shortCode={}", shortCode);
            return resolveFromCacheOrFallback(shortCode, cached.get());
        }

        //  If cache miss, DB lookup
        log.info("Cache MISS — shortCode={}, querying DB", shortCode);
        return resolveFromDatabase(shortCode);
    }


    // -------- Helper Methods --------

    /**
     * Write-through cache warm — registered AFTER repository.save() but only
     * fires AFTER the surrounding @Transactional commits.
     * This prevents caching a URL whose DB write later rolls back (ghost cache entry).
     */
    private void registerCacheAfterCommit(String shortCode, String url){

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheService.save(shortCode, url);
                    }
                }
        );
    }

    /**
     * Resolves a URL from cache and tracks the click.
     * Falls back to DB if the cached entry is stale (URL deactivated/expired since caching).
     * Evicts and throws if the cached URL itself is malformed.
     */
    private URI resolveFromCacheOrFallback(String shortCode, String cachedUrl){
        try {
            URI uri = URI.create(cachedUrl);

            // URL might be deactivated or expired in DB while still in cache.
            // If click increment fails (returns 0), evict stale entry and fall back to DB.
            if (!incrementClickIfActive(shortCode)) {
                cacheService.delete(shortCode);
                log.warn("Stale cache detected — shortCode={}, falling back to DB", shortCode);
                return resolveFromDatabase(shortCode);
            }

            return uri;
        } catch (IllegalArgumentException ex) {
            log.error("Corrupted URL in cache, evicting — shortCode={}", shortCode);
            cacheService.delete(shortCode);
            throw new InvalidStoredUrlException(shortCode);
        }
    }

    /**
     * Fetches the original URL from DB and prepares it for redirect.
     * Handles the race condition where a URL is deactivated between validation and click tracking.
     * Acts as the source of truth — DB is always trusted over cache.
     */
    private URI resolveFromDatabase(String shortCode){

        // Fetches by shortCode and validates active/expiry state in one step
        ShortUrl url = findAndValidate(shortCode);

        // Build URI FIRST (avoid caching invalid URL)
        URI uri = parseUri(url.getOriginalUrl(), shortCode);

        // Atomic click increment — guards against a race where URL is deactivated
        // between our validateUrl() check above and the actual update
        if (!incrementClickIfActive(shortCode)) {
            log.warn("URL became inactive/expired during request — shortCode={}, re-fetching latest DB state", shortCode);
            url = findAndValidate(shortCode); // re-fetch and re-validate
            uri = parseUri(url.getOriginalUrl(), shortCode);
        }

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

    // TEMP click tracking (atomic DB update)
    // Update: use Redis INCR for atomic counter, flush to DB async via scheduler.
    private boolean incrementClickIfActive(String shortCode) {
        return repository.incrementClickCountIfActive(shortCode) > 0;
    }

    // Wraps URI.create() with a meaningful exception on malformed stored URLs
    private URI parseUri(String rawUrl, String shortCode) {
        try {
            return URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid URL stored in DB — shortCode={}, url={}", shortCode, rawUrl);
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

    // Parses and validates the input URL; allows only HTTP/HTTPS and rejects malformed URLs
    private URI validateAndNormalizeUrl(String originalUrl) {

        // Defensive null/blank check (DTO validation may not always apply)
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new BadRequestException("URL must not be empty");
        }

        if (originalUrl.length() > MAX_URL_LENGTH) {
            throw new BadRequestException("URL exceeds maximum allowed length");
        }

        try {
            URI uri = URI.create(originalUrl);

            // Validate scheme (only HTTP/HTTPS allowed)
            String scheme = uri.getScheme();
            if (scheme == null ||
                    !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException("Only HTTP/HTTPS URLs are allowed");
            }

            // Validate host presence (required for proper redirection)
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BadRequestException("Invalid URL: host is missing");
            }

            return uri;

        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid URL format");
        }
    }
}
