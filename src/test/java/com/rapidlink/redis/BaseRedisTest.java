package com.rapidlink.redis;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class for all Redis + Lua script tests.
 *
 * Provides:
 * - Fully initialized Spring context (real Redis + real Lua scripts)
 * - Clean Redis state before each test (prevents flaky tests)
 * - Common helper methods for concise and readable assertions
 *
 * NOTE:
 * This class is abstract and should be extended by all Redis-related test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseRedisTest {

    @Autowired
    protected StringRedisTemplate redisTemplate;

    // Lua scripts (same beans as production)
    @Autowired
    protected DefaultRedisScript<Long> incrementAndTrackScript;

    @Autowired
    protected DefaultRedisScript<Long> moveToProcessingScript;

    @Autowired
    protected DefaultRedisScript<Long> moveToRetryAtomicScript;

    @Autowired
    protected DefaultRedisScript<Long> moveToDlqScript;

    /**
     * Runs before every test.
     * Ensures Redis is completely clean.
     */
    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .flushDb();
    }

    /**
     * Helper: get value as Long safely
     */
    protected Long getLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * Helper: set value
     */
    protected void set(String key, long value) {
        redisTemplate.opsForValue().set(key, String.valueOf(value));
    }

    /**
     * Helper: check existence
     */
    protected boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
