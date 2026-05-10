package com.rapidlink.dto.response.analytics;

import java.util.Map;
import java.util.UUID;

public record AnalyticsOverviewResponse(
        UUID shortUrlId,
        long totalClicks,
        long uniqueVisitors,
        Map<String, Long> countryBreakdown,
        Map<String, Long> deviceBreakdown,
        Map<String, Long> referrerBreakdown
) {}
