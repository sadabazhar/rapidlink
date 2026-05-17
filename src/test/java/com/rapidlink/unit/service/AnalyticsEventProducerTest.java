package com.rapidlink.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.services.impl.AnalyticsEventProducerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class AnalyticsEventProducerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, String, String> streamOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RapidLinkMetrics metrics;

    @InjectMocks
    private AnalyticsEventProducerImpl producer;

    private ClickEventRequest sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = new ClickEventRequest(
                UUID.randomUUID(),
                "hashed-ip",
                "IN",
                "Mobile",
                "https://google.com"
        );
    }

    @Test
    void shouldPublishEventSuccessfully() throws Exception {

        String serializedPayload = """
                {
                  "shortUrlId":"%s",
                  "ipHash":"hashed-ip",
                  "country":"IN",
                  "deviceType":"Mobile",
                  "referrer":"https://google.com"
                }
                """.formatted(sampleEvent.shortUrlId());

        when(objectMapper.writeValueAsString(sampleEvent))
                .thenReturn(serializedPayload);

        when(redisTemplate.opsForStream())
                .thenReturn((StreamOperations) streamOperations);

        producer.publish(sampleEvent);

        verify(objectMapper).writeValueAsString(sampleEvent);

        verify(streamOperations).add(
                any(ObjectRecord.class)
        );

        verify(metrics).recordAnalyticsPublishSuccess();
        verify(metrics, never()).recordAnalyticsPublishFailure();
    }

    @Test
    void shouldRecordFailureWhenSerializationFails() throws Exception {

        when(objectMapper.writeValueAsString(sampleEvent))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        producer.publish(sampleEvent);

        verify(objectMapper).writeValueAsString(sampleEvent);
        verify(redisTemplate, never()).opsForStream();

        verify(metrics).recordAnalyticsPublishFailure();
        verify(metrics, never()).recordAnalyticsPublishSuccess();
    }

    @Test
    void shouldRecordFailureWhenRedisPublishFails() throws Exception {

        when(objectMapper.writeValueAsString(sampleEvent))
                .thenReturn("serialized-event");

        when(redisTemplate.opsForStream())
                .thenReturn((StreamOperations) streamOperations);

        when(streamOperations.add(any(ObjectRecord.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        producer.publish(sampleEvent);

        verify(objectMapper).writeValueAsString(sampleEvent);
        verify(streamOperations).add(any(ObjectRecord.class));

        verify(metrics).recordAnalyticsPublishFailure();
        verify(metrics, never()).recordAnalyticsPublishSuccess();
    }

    @Test
    void shouldUseCorrectRedisStreamKey() throws Exception {

        when(objectMapper.writeValueAsString(sampleEvent))
                .thenReturn("payload");

        when(redisTemplate.opsForStream())
                .thenReturn((StreamOperations) streamOperations);

        producer.publish(sampleEvent);

        ArgumentCaptor<ObjectRecord<String, String>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);

        verify(streamOperations).add(captor.capture());

        ObjectRecord<String, String> capturedRecord = captor.getValue();

        assertEquals("analytics:click_events", capturedRecord.getStream());
        assertEquals("payload", capturedRecord.getValue());
    }

    @Test
    void shouldHandleUnexpectedExceptionsGracefully() throws Exception {

        when(objectMapper.writeValueAsString(sampleEvent))
                .thenThrow(new RuntimeException("Unexpected error"));

        producer.publish(sampleEvent);

        verify(metrics).recordAnalyticsPublishFailure();
        verify(metrics, never()).recordAnalyticsPublishSuccess();
    }
}
