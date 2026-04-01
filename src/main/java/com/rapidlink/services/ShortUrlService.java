package com.rapidlink.services;

import java.net.URI;

public interface ShortUrlService {

    String createShortUrl(String originalUrl);

    URI resolveUrl(String shortCode);
}
