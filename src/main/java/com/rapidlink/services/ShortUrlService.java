package com.rapidlink.services;

import com.rapidlink.entity.ShortUrl;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.util.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        ShortUrl url = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .isActive(true)
                .clickCount(0L)
                .build();

        repository.save(url);
        log.info("Short URL created successfully: shortCode={}", shortCode);

        return shortCode;
    }

    // Resolves short code with original URL and tracks click
    @Transactional // TODO: Temporary for MVP. Replace with Redis-based counter + async persistence to avoid transaction overhead and row-level contention under high traffic.
    public String resolveAndTrack(String shortCode) {

        ShortUrl url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.warn("Short URL not found for shortCode={}", shortCode);
                    return new RuntimeException("Short URL not found");
                });

        validateUrl(url);

        incrementClickCount(url);

        log.info("Redirecting shortCode={} → originalUrl={} (clickCount={})",
                shortCode, url.getOriginalUrl(), url.getClickCount());

        return url.getOriginalUrl();
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
            throw new RuntimeException("URL is deactivated");
        }

        if (url.getExpiresAt() != null &&
                url.getExpiresAt().isBefore(LocalDateTime.now())) {

            log.warn("Attempt to access expired URL: shortCode={}", url.getShortCode());
            throw new RuntimeException("URL is expired");
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
