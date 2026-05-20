package com.rapidlink.mapper;

import com.rapidlink.dto.cache.CachedShortUrl;
import com.rapidlink.entity.ShortUrl;

public final class CachedShortUrlMapper {

    private CachedShortUrlMapper() {}

    public static CachedShortUrl toCachedUrl(ShortUrl url){

        return CachedShortUrl.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .isActive(url.getIsActive())
                .expiresAt(url.getExpiresAt())
                .notFound(false)
                .build();
    }

    public static CachedShortUrl toNegativeCachedUrl(){

        return CachedShortUrl.builder()
                .notFound(true)
                .build();
    }
}
