package com.rapidlink.services;

import com.rapidlink.dto.request.analytics.ClickEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;


public interface ClickMetadataExtractor {

    ClickEventRequest extract(UUID shortUrlId, HttpServletRequest request);
}
