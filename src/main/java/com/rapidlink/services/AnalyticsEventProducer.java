package com.rapidlink.services;

import com.rapidlink.dto.request.analytics.ClickEventRequest;

public interface AnalyticsEventProducer {
    void publish(ClickEventRequest event);
}
