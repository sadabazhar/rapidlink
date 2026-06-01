package com.rapidlink.services.impl;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.services.QrCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCacheServiceImpl implements QrCacheService {

    private static final String PREFIX = "qr:";
    private static final String INDEX_PREFIX = "qr:index:";
    private static final int MAX_QR_SIZE_BYTES = 100_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, byte[]> qrRedisTemplate;
    private final RapidLinkProperties rapidLinkProperties;

    /**
     * Fetches a cached QR image from Redis.
     *
     * Returns null when:
     * - the cache entry does not exist
     * - Redis is unavailable
     * - an unexpected Redis error occurs
     *
     * Returning null allows callers to follow the cache-aside pattern
     * and regenerate the QR code when needed.
     */
    @Override
    public byte[] get(String shortCode, int size) {

        if (isInvalid(shortCode, size)) return null;

        String key = buildKey(shortCode, size);

        try {

            byte[] qrBytes = qrRedisTemplate.opsForValue().get(key);

            if (qrBytes != null && qrBytes.length > 0) {
                log.debug("QR cache HIT - key={}", key);
                return qrBytes;
            }


            log.debug("QR cache MISS - key={}", key);
            return null;

        } catch (RedisConnectionFailureException ex) {

            log.warn("Redis unavailable during QR GET - key={}", key);

            return null;

        } catch (RuntimeException ex) {

            log.error("Unexpected Redis error during QR GET - key={}", key, ex);

            return null;
        }
    }

    /**
     * Saves a QR image using the default cache TTL configured
     * in application properties.
     */
    @Override
    public void save(String shortCode, int size, byte[] qrBytes) {

        save(
                shortCode,
                size,
                qrBytes,
                rapidLinkProperties.getQr().getCacheTtl()
        );
    }

    /**
     * Saves a generated QR image in Redis.
     *
     * Stores:
     * - QR image bytes under qr:{shortCode}:{size}
     * - QR size in an index set under qr:index:{shortCode}
     *
     * The index enables efficient cache eviction without using
     * Redis KEYS pattern matching, which does not scale well
     * in large Redis deployments.
     */
    @Override
    public void save(String shortCode, int size, byte[] qrBytes, Duration ttl) {

        if (isInvalid(shortCode, size, qrBytes)) return;

        if (ttl == null
                || ttl.isNegative()
                || ttl.isZero()) {

            log.warn("QR cache save skipped - invalid TTL");

            return;
        }

        String key = buildKey(shortCode, size);
        String indexKey = buildIndexKey(shortCode);

        try {

            // Cache the generated QR image bytes
            qrRedisTemplate.opsForValue().set(key, qrBytes, ttl);

            // Maintain an index of all cached QR sizes for this short code.
            // Example: qr:index:abc123 -> 300,500,800
            stringRedisTemplate.opsForSet()
                    .add(indexKey, String.valueOf(size));

            // Keep index TTL aligned
            stringRedisTemplate.expire(indexKey, ttl);

            log.debug("QR generated and cached - shortCode={}, size={}", shortCode, size);

        } catch (RedisConnectionFailureException ex) {

            log.warn("Redis unavailable - skipping QR cache write - key={}", key);

        } catch (RuntimeException ex) {

            log.error("Unexpected Redis error during QR save - key={}", key, ex);
        }
    }

    /**
     * Removes all cached QR variants for a short code.
     *
     * Uses the QR index to locate cached sizes instead of
     * performing a Redis KEYS scan.
     *
     * Example:
     * qr:index:abc123 -> {300,500}
     *
     * Results in deletion of:
     * - qr:abc123:300
     * - qr:abc123:500
     * - qr:index:abc123
     */
    @Override
    public void delete(String shortCode) {

        if (shortCode == null || shortCode.isBlank()) {

            log.warn("QR cache delete skipped - blank shortCode");

            return;
        }

        String indexKey = buildIndexKey(shortCode);

        try {

            // Retrieve all cached QR sizes associated with this short code
            Set<String> sizes =
                    stringRedisTemplate.opsForSet()
                            .members(indexKey);

            if (sizes == null || sizes.isEmpty()) {
                log.debug(
                        "No QR cache entries found for shortCode={}",
                        shortCode
                );

                return;
            }

            // Reconstruct cache keys from indexed sizes.
            List<String> qrKeys =
                    sizes.stream()
                            .map(size ->
                                    buildKey(
                                            shortCode,
                                            Integer.parseInt(size)
                                    )
                            )
                            .toList();

            // Delete the keys and index
            qrRedisTemplate.delete(qrKeys);
            stringRedisTemplate.delete(indexKey);

            log.debug(
                    "QR cache evicted - shortCode={}, count={}",
                    shortCode,
                    qrKeys.size()
            );

        } catch (RedisConnectionFailureException ex) {

            log.warn("Redis unavailable during QR cache eviction - shortCode={}", shortCode);

        } catch (RuntimeException ex) {

            log.error("Unexpected Redis error during QR delete - shortCode={}", shortCode, ex);
        }
    }

    // Helper methods

    /**
     * Builds the Redis key for a specific QR variant.
     *
     * Example:
     * qr:abc123:300
     */
    private String buildKey(String shortCode, int size) {
        return PREFIX + shortCode + ":" + size;
    }

    /**
     * Builds the Redis index key used to track all cached
     * QR sizes for a short code.
     *
     * Example:
     * qr:index:abc123
     */
    private String buildIndexKey(String shortCode) {
        return INDEX_PREFIX + shortCode;
    }

    /**
     * Validates cache lookup parameters.
     */
    private boolean isInvalid(String shortCode, int size) {

        if (shortCode == null || shortCode.isBlank()) {

            log.warn("QR cache operation skipped - blank shortCode");

            return true;
        }

        if (size <= 0) {

            log.warn("QR cache operation skipped - invalid size={}", size);

            return true;
        }

        return false;
    }

    /**
     * Validates cache write parameters.
     *
     * Prevents:
     * - empty QR payloads
     * - oversized QR images
     * - invalid cache keys
     */
    private boolean isInvalid(String shortCode, int size, byte[] qrBytes) {

        if (isInvalid(shortCode, size)) {
            return true;
        }

        if (qrBytes == null || qrBytes.length == 0) {

            log.warn("QR cache save skipped - empty QR bytes");

            return true;
        }

        if (qrBytes.length > MAX_QR_SIZE_BYTES) {

            log.warn("QR cache save skipped - QR too large to cache");
            return true;
        }

        return false;
    }
}
