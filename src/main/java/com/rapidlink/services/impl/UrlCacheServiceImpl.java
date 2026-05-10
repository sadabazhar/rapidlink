package com.rapidlink.services.impl;

import com.rapidlink.dto.cache.CacheResult;
import com.rapidlink.dto.cache.CacheResultType;
import com.rapidlink.dto.cache.CachedShortUrl;
import com.rapidlink.mapper.CachedShortUrlMapper;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.services.UrlCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCacheServiceImpl implements UrlCacheService {

    private final RedisTemplate<String, CachedShortUrl> cachedShortUrlRedisTemplate;
    private final RapidLinkMetrics metrics;

    private static final String PREFIX = "url:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(60);

    // Fetch original URL from Redis cache
    @Override
    public CacheResult get(String shortCode) {

        if (shortCode == null || shortCode.isBlank()) return CacheResult.miss();

        String key = buildKey(shortCode);

        try{

            CachedShortUrl value = cachedShortUrlRedisTemplate.opsForValue().get(key);

            if (value == null) {
                //RecordCache miss. bcz Redis has no entry for this key.
                metrics.recordCacheMiss();

                log.debug("Cache MISS for key: {}", key);
                return CacheResult.miss();
            }

            if (isNotFoundSentinel(value)) {
                //Record negative cache hit. bcz Redis is holding a sentinel value.
                metrics.recordNegativeCacheHit();

                log.debug("Negative sentinel HIT for key: {}", key);
                return CacheResult.negative(value);
            }

            // RecordCache cache hit. bcz Redis has the original URL.
            metrics.recordCacheHit();

            log.debug("Cache HIT cache service for key: {}", key);
            return CacheResult.hit(value);

        }catch (RedisConnectionFailureException ex) {
            log.warn("Redis GET failed — key={}, falling back to DB", key);
            return CacheResult.miss();
        } catch (RuntimeException ex) {
            log.error("Unexpected Redis error during get — key={}", key, ex);
            return CacheResult.miss();
        }
    }

    // Save mapping into Redis with default TTL
    @Override
    public void save(String shortCode, CachedShortUrl cachedShortUrl) {

        if(isInvalid(shortCode, cachedShortUrl)) return;

        String key = buildKey(shortCode);

        try{

            cachedShortUrlRedisTemplate.opsForValue().set(key, cachedShortUrl, DEFAULT_TTL);
            log.debug("Cached shortCode={} with default TTL={}h", shortCode, DEFAULT_TTL.toHours());

        }catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable — skipping cache write — key={}", key);
        } catch (RuntimeException ex) {
            log.error("Unexpected Redis error during save — key={}", key, ex);
        }
    }

    // Save mapping into Redis with custom TTL
    @Override
    public void save(String shortCode, CachedShortUrl cachedShortUrl, Duration ttl) {

        if(isInvalid(shortCode, cachedShortUrl)) return;

        String key = buildKey(shortCode);

        try{

            cachedShortUrlRedisTemplate.opsForValue().set(key, cachedShortUrl, ttl);
            log.debug("Cached shortCode={} with TTL={}s", shortCode, ttl.getSeconds());

        }catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable — skipping cache write with custom TTL — key={}", key);
        } catch (RuntimeException ex) {
            log.error("Unexpected Redis error during save — key={}", key, ex);
        }
    }

    // Remove cache entry (used when URL is updated/deleted)
    @Override
    public void delete(String shortCode) {

        if (shortCode == null || shortCode.isBlank()) {
            log.warn("Skipping cache delete — shortCode is blank");
            return;
        }

        String key = buildKey(shortCode);

        try{

            cachedShortUrlRedisTemplate.delete(key);
            log.debug("Cache evicted — shortCode={}", shortCode);

        }catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable — failed to evict cache — key={}", key);
        } catch (RuntimeException ex) {
            log.error("Unexpected Redis error during delete — key={}", key, ex);
        }
    }

    /**
     * Caches the fact that this shortCode does not exist in the DB.
     * Uses a short TTL to limit staleness risk if the code is later created.
     */
    @Override
    public void saveNotFound(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            log.warn("Skipping negative cache write — shortCode is blank");
            return;
        }
        String key = buildKey(shortCode);

        // null refer to negative cache
        CachedShortUrl shortUrl = CachedShortUrlMapper.toNegativeCachedUrl();

        try {
            cachedShortUrlRedisTemplate.opsForValue().set(key, shortUrl, NEGATIVE_CACHE_TTL);
            log.debug("Negative cache entry written — shortCode={}, TTL={}m",
                    shortCode, NEGATIVE_CACHE_TTL.toMinutes());
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable — skipping negative cache write — key={}", key);
        } catch (RuntimeException ex) {
            log.error("Unexpected Redis error during saveNotFound — key={}", key, ex);
        }
    }

    /**
     * Returns true if the cached value is a negative sentinel (i.e., original url value is null as cached).
     * Callers should check this BEFORE treating the Optional value as a real URL.
     */
    @Override
    public boolean isNotFoundSentinel(CachedShortUrl cachedShortUrl) {
        return cachedShortUrl != null && cachedShortUrl.notFound();
    }

    // ---------- Helper Methods --------

    // Build Redis key with namespace
    private String buildKey(String shortCode) {
        return PREFIX + shortCode;
    }

    // preventing silent storage of empty or null values in Redis.
    private boolean isInvalid(String shortCode, CachedShortUrl cachedShortUrl) {

        if( cachedShortUrl == null){
            log.warn("Cache save skipped — short url object is blank");
            return true;
        }

        if (!shortCode.equals(cachedShortUrl.shortCode())) {
            log.warn("Cache save skipped — key/object shortcode mismatch");
            return true;
        }

        if (cachedShortUrl.shortCode().isBlank()) {
            log.warn("Cache save skipped — shortCode is blank");
            return true;
        }
        if (cachedShortUrl.originalUrl() == null || cachedShortUrl.originalUrl().isBlank()) {
            log.warn("Cache save skipped — originalUrl is blank for shortCode={}", cachedShortUrl.shortCode());
            return true;
        }

        return false;
    }
}
