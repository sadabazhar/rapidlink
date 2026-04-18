package com.rapidlink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Simple smoke test to verify that the Spring Boot application starts correctly.
 * Fails if any configuration, DB, Redis, or bean initialization is broken.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest{

    static {
        // Set JVM timezone to avoid time-related test issues
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    // Injects the Spring application context
    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // Passes if context is created successfully
        assertNotNull(context);
    }
}
