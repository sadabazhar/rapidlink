package com.rapidlink.controller;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.services.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
@Slf4j
public class QrController {

    private final QrCodeService qrCodeService;
    private final RapidLinkProperties rapidLinkProperties;

    @GetMapping("/{shortCode}")
    public ResponseEntity<byte[]> generateQrCode(
            @PathVariable String shortCode,
            @RequestParam(required = false) Integer size
    ) {

        int qrSize = Optional.ofNullable(size)
                .orElse(rapidLinkProperties.getQr().getDefaultSize());

        // Build short URL for QR generation
        String shortUrl = rapidLinkProperties.getBaseUrl() + shortCode;

        byte[] qrImage = qrCodeService.generateQrCode(shortUrl, qrSize);

        //Todo: Must remove
        log.info("Qr size :{}", qrSize);
        log.info("ShortUrl :{}", shortUrl);
        log.info("QR Img :{}", qrImage);

        return ResponseEntity.ok()

                // Response type is PNG image
                .contentType(MediaType.IMAGE_PNG)

                // Display image in browser with filename
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qr-" + shortCode + ".png\"")

                // Cache QR image for 1 day
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(qrImage);
    }
}
