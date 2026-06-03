package com.rapidlink.services.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.QrGenerationException;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.exception.UrlDeactivatedException;
import com.rapidlink.exception.UrlExpiredException;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.QrCacheService;
import com.rapidlink.services.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeServiceImpl implements QrCodeService {

    private final RapidLinkProperties rapidLinkProperties;
    private final ShortUrlRepository shortUrlRepository;
    private final QrCacheService qrCacheService;
    private final RapidLinkMetrics rapidLinkMetrics;

    /**
     * Returns a QR image for the given short code.
     * First checks Redis cache, then generates and caches the QR if needed.
     */
    @Override
    public byte[] getQrCode(String shortCode, int size){

        return rapidLinkMetrics.timeQrGenerationResponse(() -> {
            rapidLinkMetrics.recordQrRequest();

            validateRequest(shortCode, size);

            // Check if QR image is already cached
            byte[] qrByte = qrCacheService.get(shortCode, size);

            // Cache Hit
            if (qrByte != null){

                log.debug(
                        "Returning QR from cache - shortCode={}, size={}",
                        shortCode,
                        size
                );

                rapidLinkMetrics.recordQrGenerationSuccess();
                return qrByte;
            }

            // Generate and cache QR when not found in cache
            ShortUrl shortUrl = validateShortCode(shortCode);
            qrByte = generateQrCode(shortCode, size);

            // Use URL expiration time as QR cache TTL
            Duration ttl = resolveCacheTtl(shortUrl);
            qrCacheService.save(shortCode, size, qrByte, ttl);
            log.debug(
                    "QR generated and cached - shortCode={}, size={}",
                    shortCode,
                    size
            );

            rapidLinkMetrics.recordQrGenerationSuccess();

            return qrByte;
        });

    }

    // Helper methods

    /**
     * Generates a QR image from the RapidLink short URL.
     */
    private byte[] generateQrCode(String shortCode, int size) {

        // Create the full URL that will be encoded in the QR
        String content = buildShortUrl(shortCode);

        try {

            // QR generation settings
            Map<EncodeHintType, Object> hints = buildHints();

            // Create QR matrix from URL content
            QRCodeWriter qrCodeWriter = new QRCodeWriter();

            BitMatrix bitMatrix = qrCodeWriter.encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    hints
            );

            // Convert Matrix to given format (PNG, JPG, SVG)
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(
                    bitMatrix,
                    rapidLinkProperties.getQr().getFormat(),
                    outputStream
            );

            return outputStream.toByteArray();

        } catch (WriterException | IOException ex) {

            rapidLinkMetrics.recordQrGenerationFailure();

            throw new QrGenerationException(
                    "Failed to generate QR code"
            );
        }
    }

    /**
     * Creates QR generation settings used by ZXing.
     */
    private Map<EncodeHintType, Object> buildHints(){

        Map<EncodeHintType, Object> hints = new HashMap<>();

        // Support UTF-8 content
        hints.put(
                EncodeHintType.CHARACTER_SET,
                StandardCharsets.UTF_8.name()
        );

        // Allow QR recovery if partially damaged
        hints.put(
                EncodeHintType.ERROR_CORRECTION,
                ErrorCorrectionLevel.M
        );

        // White border around QR
        hints.put(
                EncodeHintType.MARGIN,
                1
        );

        return hints;

    }

    /**
     * Validates QR request parameters.
     */
    private void validateRequest(String shortCode, int size) {

        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException(
                    "short code cannot be empty"
            );
        }

        if (size < rapidLinkProperties.getQr().getMinSize()
                || size > rapidLinkProperties.getQr().getMaxSize()) {

            throw new IllegalArgumentException(
                    "QR size must be between "
                            + rapidLinkProperties.getQr().getMinSize()
                            + " and " + rapidLinkProperties.getQr().getMaxSize()
            );
        }
    }

    /**
     * Verifies that the short URL exists and can still be used.
     */
    private ShortUrl validateShortCode(String shortCode){

        // Load short URL from database
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(()-> new ShortUrlNotFoundException("Short URL not found"));

        // Block QR generation for inactive URLs
        if (Boolean.FALSE.equals(shortUrl.getIsActive())) {
            throw new UrlDeactivatedException(
                    "Short URL is not active"
            );
        }

        // Block QR generation for expired URLs
        if (shortUrl.getExpiresAt() != null
                && shortUrl.getExpiresAt().isBefore(LocalDateTime.now())) {

            throw new UrlExpiredException(
                    "Short URL is expired"
            );
        }

        return shortUrl;
    }

    /**
     * Builds the full RapidLink URL from a short code.
     */
    private String buildShortUrl(String shortCode) {
        return rapidLinkProperties.getBaseUrl() + shortCode;
    }

    /**
     * Determines how long the QR should stay in cache.
     * If the URL expires, the QR cache expires with it.
     */
    private Duration resolveCacheTtl(ShortUrl shortUrl) {

        // Use default cache TTL when URL never expires
        if (shortUrl.getExpiresAt() == null) {
            return rapidLinkProperties.getQr().getCacheTtl();
        }

        // Match QR cache lifetime with URL lifetime
        return Duration.between(
                LocalDateTime.now(),
                shortUrl.getExpiresAt()
        );
    }
}
