package com.rapidlink.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumerGroupInitializer {

    private static final String STREAM_KEY = "analytics:click_events";
    private static final String GROUP_NAME = "analytics_group";

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        try {

            redisTemplate.opsForStream().createGroup(
                    STREAM_KEY,
                    ReadOffset.latest(),
                    GROUP_NAME
            );

            log.info("Created Redis consumer group: {}", GROUP_NAME);

        } catch (Exception ex) {

            if (isBusyGroupException(ex)) {

                log.info("Consumer group already exists: {}", GROUP_NAME);

            } else {

                log.error("Failed to create consumer group", ex);
                throw new IllegalStateException("Analytics consumer group initialization failed", ex);
            }
        }
    }

    private boolean isBusyGroupException(Throwable ex) {

        while (ex != null) {

            if (ex.getMessage() != null &&
                    ex.getMessage().contains("BUSYGROUP")) {
                return true;
            }

            ex = ex.getCause();
        }

        return false;
    }
}
