package com.rapidlink.validation;

import com.rapidlink.exception.BadRequestException;
import java.net.URI;

/**
 * Utility class for validating and normalizing input URLs.
 * Ensures URLs are well-formed and use HTTP/HTTPS.
 */
public class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    private UrlValidator() {
        // prevent instantiation
    }

    // Parses and validates the input URL; allows only HTTP/HTTPS and rejects malformed URLs
    public static URI validate(String originalUrl){

        // Defensive null/blank check (DTO validation may not always apply)
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new BadRequestException("URL must not be empty");
        }

        if (originalUrl.length() > MAX_URL_LENGTH) {
            throw new BadRequestException("URL exceeds maximum allowed length");
        }

        try {
            URI uri = URI.create(originalUrl);

            // Validate scheme (only HTTP/HTTPS allowed)
            String scheme = uri.getScheme();
            if (scheme == null ||
                    !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {

                throw new BadRequestException("Only HTTP/HTTPS URLs are allowed");
            }

            // Validate host presence (required for proper redirection)
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BadRequestException("Invalid URL: host is missing");
            }

            return uri;

        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid URL format");
        }
    }
}
