package com.rapidlink.services.impl;

import com.rapidlink.dto.cache.CachedShortUrl;
import com.rapidlink.encoder.Base62Encoder;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.BadRequestException;
import com.rapidlink.exception.ShortCodeGenerationException;
import com.rapidlink.mapper.CachedShortUrlMapper;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.UrlCacheService;
import com.rapidlink.services.UrlManagementService;
import com.rapidlink.validation.UrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
class UrlManagementServiceImpl implements UrlManagementService {

    private final RapidLinkMetrics metrics;
    private final ShortUrlRepository repository;
    private final UrlCacheService cacheService;

    // Creates a short URL for the given original URL
    @Override
    @Transactional
    public String createUrl(String originalUrl) {

        // timeUrlCreate() wraps the ENTIRE creation flow
        return metrics.timeUrlCreate(() -> {

            log.info("Creating short URL request received");

            URI validatedUri;

            try {
                validatedUri = UrlValidator.validate(originalUrl);
            } catch (BadRequestException ex) {
                metrics.recordUrlCreateFailure();
                log.error("Url is invalid url={}", originalUrl, ex);
                throw ex;
            }

            String normalizedUrl = validatedUri.toString();

            Long seqId = repository.nextSeqId();
            if (seqId == null) {

                // record Url creation failure
                metrics.recordUrlCreateFailure();
                throw new ShortCodeGenerationException("DB Sequence returned null");
            }

            // TODO: Current implementation is predictable (seq_id → Base62).
            // Consider adding obfuscation (e.g., hashing or ID scrambling)
            // to prevent enumeration attacks in public URLs.
            String shortCode = Base62Encoder.encode(seqId);

            ShortUrl url = ShortUrl.builder()
                    .originalUrl(normalizedUrl)
                    .seqId(seqId)
                    .shortCode(shortCode)
                    .isActive(true)
                    .clickCount(0L)
                    .build();

            try {
                // Force immediate DB flush so constraint violations are thrown here
                // (otherwise exceptions occur at transaction commit, outside this try-catch)
                url = repository.saveAndFlush(url);
            } catch (DataIntegrityViolationException ex) {

                // record Url creation failure
                metrics.recordUrlCreateFailure();
                log.error("DB constraint violation during short URL creation — shortCode={}", shortCode, ex);
                throw new ShortCodeGenerationException();
            }

            CachedShortUrl shortUrl = CachedShortUrlMapper.toCachedUrl(url);

            // Write-through cache warm — fires only AFTER @Transactional commits
            registerCacheAfterCommit(shortCode, shortUrl);

            // TODO: Update setActiveUrlCount() periodically (scheduler) or maintain counter manually (event-driven)
            // Update active URL gauge AFTER successful DB save.
            // This keeps the gauge accurate even if URLs are expired
            long activeUrlCount = repository.countByExpiresAtAfterOrExpiresAtIsNull(LocalDateTime.now());
            metrics.setActiveUrlCount(activeUrlCount);

            // Set Url creation success
            metrics.recordUrlCreateSuccess();

            log.info("Short URL created successfully: shortCode={}", shortCode);
            return shortCode;
        });
    }

    /**
     * Write-through cache warm — registered AFTER repository.save() but only
     * fires AFTER the surrounding @Transactional commits.
     * This prevents caching a URL whose DB write later rolls back (ghost cache entry).
     */
    private void registerCacheAfterCommit(String shortCode, CachedShortUrl shortUrl){

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheService.save(shortCode, shortUrl); // warm with real URL
                    }
                }
        );
    }
}
