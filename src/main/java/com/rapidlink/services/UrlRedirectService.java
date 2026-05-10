package com.rapidlink.services;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

public interface UrlRedirectService {
    URI getRedirectUrl(String shortCode, HttpServletRequest request);
}