package com.rapidlink.services;

import com.rapidlink.dto.response.analytics.AnalyticsOverviewResponse;

public interface AnalyticsQueryService {

    AnalyticsOverviewResponse getOverview(String shortCode);
}
