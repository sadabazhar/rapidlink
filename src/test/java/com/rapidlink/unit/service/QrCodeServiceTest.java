package com.rapidlink.unit.service;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.exception.UrlDeactivatedException;
import com.rapidlink.exception.UrlExpiredException;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.QrCacheService;
import com.rapidlink.services.impl.QrCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class QrCodeServiceTest {

    @Mock
    private RapidLinkProperties rapidLinkProperties;

    @Mock
    private RapidLinkProperties.Qr qrProperties;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private QrCacheService qrCacheService;

    @Mock
    private RapidLinkMetrics rapidLinkMetrics;

    @InjectMocks
    private QrCodeServiceImpl qrCodeService;

    @BeforeEach
    void setUp() {

        lenient().when(rapidLinkProperties.getQr())
                .thenReturn(qrProperties);

        lenient().when(qrProperties.getMinSize())
                .thenReturn(100);

        lenient().when(qrProperties.getMaxSize())
                .thenReturn(1000);

        when(rapidLinkMetrics.timeQrGenerationResponse(any()))
                .thenAnswer(invocation -> {
                    Supplier<byte[]> supplier =
                            invocation.getArgument(0);
                    return supplier.get();
                });
    }

    @Test
    void shouldReturnQrFromCache() {

        byte[] cachedQr = {1, 2, 3};

        when(qrCacheService.get("abc123", 300))
                .thenReturn(cachedQr);

        byte[] result =
                qrCodeService.getQrCode("abc123", 300);

        assertArrayEquals(cachedQr, result);

        verify(shortUrlRepository, never())
                .findByShortCode(any());

        verify(qrCacheService, never())
                .save(any(), anyInt(), any(), any());

        verify(rapidLinkMetrics)
                .recordQrGenerationSuccess();
    }

    @Test
    void shouldGenerateAndCacheQrWhenCacheMiss() {

        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode("abc123")
                .isActive(true)
                .build();

        when(qrProperties.getFormat())
                .thenReturn("PNG");

        when(qrProperties.getCacheTtl())
                .thenReturn(Duration.ofHours(24));

        when(rapidLinkProperties.getBaseUrl())
                .thenReturn("https://rapidlink.com/");

        when(qrCacheService.get("abc123", 300))
                .thenReturn(null);

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        byte[] result =
                qrCodeService.getQrCode("abc123", 300);

        assertNotNull(result);
        assertTrue(result.length > 0);

        verify(qrCacheService)
                .save(
                        eq("abc123"),
                        eq(300),
                        any(byte[].class),
                        eq(Duration.ofHours(24))
                );

        verify(rapidLinkMetrics)
                .recordQrGenerationSuccess();
    }

    @Test
    void shouldThrowWhenShortCodeIsNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> qrCodeService.getQrCode(null, 300)
        );

        verifyNoInteractions(shortUrlRepository);
    }

    @Test
    void shouldThrowWhenShortCodeIsBlank() {

        assertThrows(
                IllegalArgumentException.class,
                () -> qrCodeService.getQrCode(" ", 300)
        );

        verifyNoInteractions(shortUrlRepository);
    }

    @Test
    void shouldThrowWhenSizeBelowMinimum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> qrCodeService.getQrCode("abc123", 50)
        );
    }

    @Test
    void shouldThrowWhenSizeAboveMaximum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> qrCodeService.getQrCode("abc123", 2000)
        );
    }

    @Test
    void shouldThrowWhenShortUrlNotFound() {

        when(qrCacheService.get("abc123", 300))
                .thenReturn(null);

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.empty());

        assertThrows(
                ShortUrlNotFoundException.class,
                () -> qrCodeService.getQrCode("abc123", 300)
        );
    }

    @Test
    void shouldThrowWhenShortUrlInactive() {

        ShortUrl shortUrl = ShortUrl.builder()
                .isActive(false)
                .build();

        when(qrCacheService.get("abc123", 300))
                .thenReturn(null);

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        assertThrows(
                UrlDeactivatedException.class,
                () -> qrCodeService.getQrCode("abc123", 300)
        );
    }

    @Test
    void shouldThrowWhenShortUrlExpired() {

        ShortUrl shortUrl = ShortUrl.builder()
                .isActive(true)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        when(qrCacheService.get("abc123", 300))
                .thenReturn(null);

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        assertThrows(
                UrlExpiredException.class,
                () -> qrCodeService.getQrCode("abc123", 300)
        );
    }

    @Test
    void shouldUseUrlExpirationAsCacheTtl() {

        LocalDateTime expiresAt =
                LocalDateTime.now().plusHours(2);

        ShortUrl shortUrl = ShortUrl.builder()
                .isActive(true)
                .expiresAt(expiresAt)
                .build();

        when(qrProperties.getFormat())
                .thenReturn("PNG");

        when(rapidLinkProperties.getBaseUrl())
                .thenReturn("https://rapidlink.com/");

        when(qrCacheService.get("abc123", 300))
                .thenReturn(null);

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        qrCodeService.getQrCode("abc123", 300);

        ArgumentCaptor<Duration> ttlCaptor =
                ArgumentCaptor.forClass(Duration.class);

        verify(qrCacheService)
                .save(
                        eq("abc123"),
                        eq(300),
                        any(byte[].class),
                        ttlCaptor.capture()
                );

        Duration ttl = ttlCaptor.getValue();

        assertTrue(ttl.toMinutes() >= 119);
        assertTrue(ttl.toMinutes() <= 120);
    }

    @Test
    void shouldRecordRequestMetric() {

        byte[] cachedQr = {1};

        when(qrCacheService.get("abc123", 300))
                .thenReturn(cachedQr);

        qrCodeService.getQrCode("abc123", 300);

        verify(rapidLinkMetrics)
                .recordQrRequest();
    }

    @Test
    void shouldRecordSuccessMetricOnCacheHit() {

        byte[] cachedQr = {1};

        when(qrCacheService.get("abc123", 300))
                .thenReturn(cachedQr);

        qrCodeService.getQrCode("abc123", 300);

        verify(rapidLinkMetrics)
                .recordQrGenerationSuccess();
    }
}
