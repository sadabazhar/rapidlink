package com.rapidlink.services;

import java.time.Duration;

public interface QrCacheService {

    byte[] get(String shortCode, int size);

    void save(String shortCode, int size, byte[] qrBytes);

    void save(String shortCode, int size, byte[] qrBytes, Duration ttl);

    void delete(String shortCode);
}
