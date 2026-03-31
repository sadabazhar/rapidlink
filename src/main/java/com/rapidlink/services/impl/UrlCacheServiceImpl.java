package com.rapidlink.services.impl;

import com.rapidlink.services.UrlCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCacheServiceImpl implements UrlCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "url:";
    private static final Duration TTL = Duration.ofHours(24);

    // Fetch original URL from Redis cache
    @Override
    public Optional<String> get(String shortCode) {

        if (shortCode == null || shortCode.isBlank()) {
            return Optional.empty();
        }

        String key = buildKey(shortCode);
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.debug("Cache MISS for key: {}", key);
            return Optional.empty();
        }

        log.debug("Cache HIT cache service for key: {}", key);
        return Optional.of(value);
    }

    // Save mapping into Redis with default TTL
    @Override
    public void save(String shortCode, String originalUrl) {

        if(isValid(shortCode, originalUrl)) return;

        String key = buildKey(shortCode);
        redisTemplate.opsForValue().set(key, originalUrl, TTL);
        log.debug("Cached shortCode={} with default TTL={}h", shortCode, TTL.toHours());
    }

    // Save mapping into Redis with custom TTL
    @Override
    public void save(String shortCode, String originalUrl, Duration ttl) {

        if(isValid(shortCode, originalUrl)) return;

        String key = buildKey(shortCode);
        redisTemplate.opsForValue().set(key, originalUrl, ttl);
        log.debug("Cached shortCode={} with TTL={}s", shortCode, ttl.getSeconds());
    }

    // Remove cache entry (used when URL is updated/deleted)
    @Override
    public void delete(String shortCode) {

        if (shortCode == null || shortCode.isBlank()) {
            log.warn("Skipping cache delete — shortCode is blank");
            return;
        }
        redisTemplate.delete(buildKey(shortCode));
        log.debug("Cache evicted — shortCode={}", shortCode);
    }

    // Build Redis key with namespace
    private String buildKey(String shortCode) {
        return PREFIX + shortCode;
    }

    // preventing silent storage of empty or null values in Redis.
    private boolean isValid(String shortCode, String originalUrl) {
        if (shortCode == null || shortCode.isBlank()) {
            log.warn("Cache save skipped — shortCode is blank");
            return true;
        }
        if (originalUrl == null || originalUrl.isBlank()) {
            log.warn("Cache save skipped — originalUrl is blank for shortCode={}", shortCode);
            return true;
        }
        return false;
    }
}
