package com.rapidlink.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.services.AnalyticsEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.rapidlink.metrics.RapidLinkMetrics;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventProducerImpl implements AnalyticsEventProducer {

    private static final String STREAM_KEY = "analytics:click_events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RapidLinkMetrics metrics;

    @Override
    public void publish(ClickEventRequest event) {

        if (event == null) {
            metrics.recordAnalyticsPublishFailure();
            log.error("Failed to publish analytics event: event is null");
            return;
        }

        String shortUrlId = String.valueOf(event.shortUrlId());

        try {
            String payload = objectMapper.writeValueAsString(event);

            ObjectRecord<String, String> eventRecord = StreamRecords
                    .newRecord()
                    .ofObject(payload)
                    .withStreamKey(STREAM_KEY);

            redisTemplate.opsForStream().add(eventRecord);

            metrics.recordAnalyticsPublishSuccess();

            log.debug("Published analytics event to Redis Stream for shortUrlId={}", shortUrlId);

        } catch (JsonProcessingException e) {

            metrics.recordAnalyticsPublishFailure();

            log.error("Failed to serialize analytics event for shortUrlId={}", shortUrlId, e);

        } catch (Exception e) {

            metrics.recordAnalyticsPublishFailure();

            log.error("Failed to publish analytics event to Redis Stream for shortUrlId={}", shortUrlId, e);
        }
    }
}
