package com.rapidlink;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.TimeZone;

/**
 * Base class for all integration tests.
 *
 * Loads full Spring Boot context with test profile,
 * configures MockMvc, and provides common test utilities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static {
        // Set JVM timezone to avoid time-related test issues
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Autowired
    protected StringRedisTemplate redisTemplate;

    // Clear Redis after each test to keep tests isolated and repeatable
    @AfterEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {

            // Delete all keys in current Redis DB
            connection.serverCommands().flushDb();
            return null;
        });
    }
}