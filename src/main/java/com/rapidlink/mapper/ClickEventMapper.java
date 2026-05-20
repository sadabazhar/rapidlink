package com.rapidlink.mapper;

import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.entity.ClickEvent;
import com.rapidlink.entity.ShortUrl;

public final class ClickEventMapper {

    private ClickEventMapper() {}

    public static ClickEvent toEntity(ClickEventRequest request, ShortUrl shortUrl) {
        return ClickEvent.builder()
                .shortUrl(shortUrl)
                .ipHash(request.ipHash())
                .country(request.country())
                .deviceType(request.deviceType())
                .referrer(request.referrer())
                .build();
    }
}
