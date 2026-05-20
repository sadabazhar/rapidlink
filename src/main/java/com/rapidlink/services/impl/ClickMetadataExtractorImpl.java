package com.rapidlink.services.impl;

import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.services.ClickMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Extracts analytics metadata from incoming redirect requests
 * and converts it into a ClickEventRequest DTO.
 *
 * Responsibilities:
 * - Privacy-safe IP hashing
 * - Country extraction (basic for Phase 1)
 * - Device type detection
 * - Referrer extraction
 */

@Service
@Slf4j
public class ClickMetadataExtractorImpl implements ClickMetadataExtractor {

    private static final String UNKNOWN = "UNKNOWN";

    /**
     * Builds analytics event metadata for a given short URL click.
     *
     * @param shortUrlId unique identifier of the short URL
     * @param request incoming HTTP request
     * @return populated ClickEventRequest
     */
    @Override
    public ClickEventRequest extract(UUID shortUrlId, HttpServletRequest request) {
        String ip = extractClientIp(request);
        String ipHash = hashIp(ip);

        String country = extractCountry(ip);
        String deviceType = extractDeviceType(request.getHeader("User-Agent"));
        String referrer = extractReferrer(request);

        return new ClickEventRequest(
                shortUrlId,
                ipHash,
                country,
                deviceType,
                referrer
        );
    }

    /**
     * Extracts client IP address.
     * Handles proxy headers for production deployments.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Generates privacy-safe SHA-256 hash of visitor IP.
     */
    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to hash IP address", e);
            return null;
        }
    }

    /**
     * Basic placeholder country extraction.
     *
     * Phase 1:
     * Can return UNKNOWN until GeoIP integration is added.
     *
     * Future:
     * Integrate MaxMind GeoLite2 or external GeoIP service.
     */
    private String extractCountry(String ip) {
        return UNKNOWN;
    }

    /**
     * Basic device classification using User-Agent.
     */
    private String extractDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("mobile")) {
            return "Mobile";
        }

        if (ua.contains("tablet")) {
            return "Tablet";
        }

        return "Desktop";
    }

    /**
     * Extracts HTTP referrer.
     */
    private String extractReferrer(HttpServletRequest request) {
        String referrer = request.getHeader("Referer");

        if (referrer == null || referrer.isBlank()) {
            return "Direct";
        }

        return referrer;
    }
}
