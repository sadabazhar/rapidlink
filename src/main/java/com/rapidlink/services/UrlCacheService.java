package com.rapidlink.services;

import com.rapidlink.dto.cache.CacheResult;
import com.rapidlink.dto.cache.CachedShortUrl;
import java.time.Duration;

public interface UrlCacheService {

    CacheResult get(String shortCode);

    void save(String shortCode, CachedShortUrl cachedShortUrl);

    void save(String shortCode, CachedShortUrl cachedShortUrl, Duration ttl);

    void delete(String shortCode);

    boolean isNotFoundSentinel(CachedShortUrl cachedShortUrl);

    void saveNotFound(String shortCode);
}
