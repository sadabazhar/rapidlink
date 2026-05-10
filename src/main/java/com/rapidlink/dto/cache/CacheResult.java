package com.rapidlink.dto.cache;

public record CacheResult(
        CacheResultType type,
        CachedShortUrl value
) {
    public static CacheResult miss() {
        return new CacheResult(CacheResultType.MISS, null);
    }

    public static CacheResult hit(CachedShortUrl value) {
        return new CacheResult(CacheResultType.HIT, value);
    }

    public static CacheResult negative(CachedShortUrl value) {
        return new CacheResult(CacheResultType.NEGATIVE_HIT, value);
    }
}
