package com.rapidlink.services;

import java.net.URI;

public interface UrlRedirectService {
    URI getRedirectUrl(String shortCode);
}