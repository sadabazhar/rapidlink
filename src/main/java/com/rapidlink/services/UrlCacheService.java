package com.rapidlink.services;

import java.time.Duration;
import java.util.Optional;

public interface UrlCacheService {

    Optional<String> get(String shortCode);

    void save(String shortCode, String originalUrl);

    void save(String shortCode, String originalUrl, Duration ttl);

    void delete(String shortCode);
}
