package com.rapidlink.dto.cache;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record CachedShortUrl(
        UUID id,
        String shortCode,
        String originalUrl,
        Boolean isActive,
        LocalDateTime expiresAt,
        Boolean notFound
) {}
