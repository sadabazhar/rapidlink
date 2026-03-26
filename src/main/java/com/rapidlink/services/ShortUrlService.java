package com.rapidlink.services;

import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.*;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.util.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortUrlService {

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator generator;

    // Creates a short URL for the given original URL
    @Transactional
    public String createShortUrl(String originalUrl) {

        log.info("Creating short URL for originalUrl={}", originalUrl);

        String shortCode = generateUniqueCode();
        URI validatedUri = validateAndNormalizeUrl(originalUrl);

        ShortUrl url = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(validatedUri.toString())
                .isActive(true)
                .clickCount(0L)
                .build();

        repository.save(url);
        log.info("Short URL created successfully: shortCode={}", shortCode);

        return shortCode;
    }

    // Resolves short code with original URL and tracks click
    @Transactional // TODO: Temporary for MVP. Replace with Redis-based counter + async persistence to avoid transaction overhead and row-level contention under high traffic.
    public URI resolveAndTrack(String shortCode) {

        ShortUrl url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.warn("Short URL not found for shortCode={}", shortCode);
                    return new ShortUrlNotFoundException("Short URL not found, with this shortcode : " + shortCode);
                });

        validateUrl(url);

        incrementClickCount(url);

        log.info("Redirecting shortCode={} (clickCount={})",
                shortCode, url.getClickCount());

        // Ensure stored URL is valid before redirecting
        try {
            return URI.create(url.getOriginalUrl());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid URL stored in DB: shortCode={}", shortCode);
            throw new InvalidStoredUrlException(shortCode);
        }
    }


    // Generates unique short code with retry on collision
    private String generateUniqueCode() {
        String code;
        do {
            code = generator.generate();
        } while (repository.existsByShortCode(code));

        return code;
    }

    // Validates if URL is active and not expired
    private void validateUrl(ShortUrl url) {

        if (!url.getIsActive()) {
            log.warn("Attempt to access deactivated URL: shortCode={}", url.getShortCode());
            throw new UrlDeactivatedException("Short URL is deactivated, with this shortcode : " + url.getShortCode());
        }

        if (url.getExpiresAt() != null &&
                url.getExpiresAt().isBefore(LocalDateTime.now())) {

            log.warn("Attempt to access expired URL: shortCode={}", url.getShortCode());
            throw new UrlExpiredException("Short URL is expired, with this shortcode : " + url.getShortCode());
        }
    }

    // Parses and validates the input URL; allows only HTTP/HTTPS and rejects malformed URLs
    private URI validateAndNormalizeUrl(String originalUrl) {

        // Defensive null/blank check (DTO validation may not always apply)
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new BadRequestException("URL must not be empty");
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

    /**
     * Increments click count (basic implementation)
     * TODO: Replace with async/event-based tracking for high scale
     */
    private void incrementClickCount(ShortUrl url) {
        url.setClickCount(url.getClickCount() + 1);
        repository.save(url);
    }
}
