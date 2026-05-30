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
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.QrCodeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class QrCodeServiceImpl implements QrCodeService {

    private RapidLinkProperties rapidLinkProperties;
    private final ShortUrlRepository shortUrlRepository;

    @Override
    public byte[] generateQrCode(String shortCode, int size) {

        validateRequest(shortCode, size);
        validateShortCode(shortCode);

        String content = buildShortUrl(shortCode);

        try {
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

            // Generate Matrix
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
            throw new QrGenerationException(
                    "Failed to generate QR code"
            );
        }
    }

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

    private void validateShortCode(String shortCode){
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(()-> new ShortUrlNotFoundException("Short URL not found"));

        if (Boolean.FALSE.equals(shortUrl.getIsActive())) {
            throw new UrlDeactivatedException(
                    "Short URL is not active"
            );
        }

        if (shortUrl.getExpiresAt() != null
                && shortUrl.getExpiresAt().isBefore(LocalDateTime.now())) {

            throw new UrlExpiredException(
                    "Short URL is expired"
            );
        }

    }

    private String buildShortUrl(String shortCode) {
        return rapidLinkProperties.getBaseUrl() + shortCode;
    }
}
