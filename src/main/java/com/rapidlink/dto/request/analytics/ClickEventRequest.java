package com.rapidlink.dto.request.analytics;

import java.util.UUID;

public record ClickEventRequest(
        UUID shortUrlId,
        String ipHash,
        String country,
        String deviceType,
        String referrer
) {}
