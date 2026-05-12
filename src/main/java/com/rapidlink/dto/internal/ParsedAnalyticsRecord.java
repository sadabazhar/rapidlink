package com.rapidlink.dto.internal;

import com.rapidlink.dto.request.analytics.ClickEventRequest;
import org.springframework.data.redis.connection.stream.MapRecord;

public record ParsedAnalyticsRecord(
        MapRecord<String, Object, Object> record,
        ClickEventRequest request
) {}