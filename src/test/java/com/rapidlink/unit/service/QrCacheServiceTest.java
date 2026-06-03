package com.rapidlink.unit.service;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.services.impl.QrCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisTemplate<String, byte[]> qrRedisTemplate;

    @Mock
    private ValueOperations<String, byte[]> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private RapidLinkProperties rapidLinkProperties;

    @Mock
    private RapidLinkProperties.Qr qrProperties;

    @Mock
    private RapidLinkMetrics rapidLinkMetrics;

    private QrCacheServiceImpl qrCacheService;

    @BeforeEach
    void setUp() {

        qrCacheService =
                new QrCacheServiceImpl(
                        stringRedisTemplate,
                        qrRedisTemplate,
                        rapidLinkProperties,
                        rapidLinkMetrics
                );

        lenient().when(qrRedisTemplate.opsForValue())
                .thenReturn(valueOperations);

        lenient().when(stringRedisTemplate.opsForSet())
                .thenReturn(setOperations);

        lenient().when(rapidLinkProperties.getQr())
                .thenReturn(qrProperties);

        lenient().when(qrProperties.getCacheTtl())
                .thenReturn(Duration.ofHours(24));
    }

    @Test
    void shouldReturnCachedQrWhenPresent() {

        byte[] qrBytes = {1, 2, 3};

        when(valueOperations.get(anyString()))
                .thenReturn(qrBytes);

        byte[] result =
                qrCacheService.get("abc123", 300);

        assertArrayEquals(qrBytes, result);

        verify(rapidLinkMetrics)
                .recordQrCacheHit();

        verify(rapidLinkMetrics, never())
                .recordQrCacheMiss();
    }

    @Test
    void shouldReturnNullWhenCacheMiss() {

        when(valueOperations.get("qr:abc123:300"))
                .thenReturn(null);

        byte[] result =
                qrCacheService.get("abc123", 300);

        assertNull(result);

        verify(rapidLinkMetrics)
                .recordQrCacheMiss();
    }

    @Test
    void shouldReturnNullWhenCachedBytesAreEmpty() {

        when(valueOperations.get(anyString()))
                .thenReturn(new byte[0]);

        byte[] result =
                qrCacheService.get("abc123", 300);

        assertNull(result);

        verify(rapidLinkMetrics)
                .recordQrCacheMiss();
    }

    @Test
    void shouldReturnNullWhenRedisConnectionFailsOnGet() {

        when(valueOperations.get(anyString()))
                .thenThrow(
                        new RedisConnectionFailureException("redis down")
                );

        byte[] result =
                qrCacheService.get("abc123", 300);

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenUnexpectedRedisErrorOccursOnGet() {

        lenient().when(valueOperations.get(anyString()))
                .thenThrow(new RuntimeException("boom"));

        byte[] result =
                qrCacheService.get("abc123", 300);

        assertNull(result);
    }

    @Test
    void shouldReturnNullForBlankShortCode() {

        byte[] result =
                qrCacheService.get("", 300);

        assertNull(result);

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldReturnNullForInvalidSize() {

        byte[] result =
                qrCacheService.get("abc123", 0);

        assertNull(result);

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSaveQrSuccessfully() {

        byte[] qrBytes = {1, 2, 3};

        Duration ttl = Duration.ofHours(1);

        qrCacheService.save(
                "abc123",
                300,
                qrBytes,
                ttl
        );

        verify(valueOperations)
                .set(
                        "qr:abc123:300",
                        qrBytes,
                        ttl
                );

        verify(setOperations)
                .add(
                        "qr:index:abc123",
                        "300"
                );

        verify(stringRedisTemplate)
                .expire(
                        "qr:index:abc123",
                        ttl
                );
    }

    @Test
    void shouldUseDefaultTtlWhenSaveWithoutExplicitTtl() {

        byte[] qrBytes = {1, 2, 3};

        when(rapidLinkProperties.getQr())
                .thenReturn(qrProperties);

        when(qrProperties.getCacheTtl())
                .thenReturn(Duration.ofHours(24));

        qrCacheService.save(
                "abc123",
                300,
                qrBytes
        );

        verify(valueOperations)
                .set(
                        eq("qr:abc123:300"),
                        eq(qrBytes),
                        eq(Duration.ofHours(24))
                );
    }

    @Test
    void shouldSkipSaveWhenQrBytesNull() {

        qrCacheService.save(
                "abc123",
                300,
                null,
                Duration.ofHours(1)
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSkipSaveWhenQrBytesEmpty() {

        qrCacheService.save(
                "abc123",
                300,
                new byte[0],
                Duration.ofHours(1)
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSkipSaveWhenQrBytesTooLarge() {

        byte[] bytes = new byte[100_001];

        qrCacheService.save(
                "abc123",
                300,
                bytes,
                Duration.ofHours(1)
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSkipSaveWhenTtlIsNull() {

        qrCacheService.save(
                "abc123",
                300,
                new byte[]{1},
                null
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSkipSaveWhenTtlIsZero() {

        qrCacheService.save(
                "abc123",
                300,
                new byte[]{1},
                Duration.ZERO
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldSkipSaveWhenTtlIsNegative() {

        qrCacheService.save(
                "abc123",
                300,
                new byte[]{1},
                Duration.ofSeconds(-1)
        );

        verifyNoInteractions(valueOperations);
    }

    @Test
    void shouldIgnoreRedisConnectionFailureOnSave() {

        lenient().doThrow(
                        new RedisConnectionFailureException("redis down")
                ).when(valueOperations)
                .set(anyString(), any(), any(Duration.class));

        assertDoesNotThrow(() ->
                qrCacheService.save(
                        "abc123",
                        300,
                        new byte[]{1},
                        Duration.ofHours(1)
                )
        );
    }

    @Test
    void shouldDeleteAllCachedVariants() {

        Set<String> sizes = Set.of("300", "500");

        when(setOperations.members("qr:index:abc123"))
                .thenReturn(sizes);

        qrCacheService.delete("abc123");

        ArgumentCaptor<List<String>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(qrRedisTemplate)
                .delete(captor.capture());

        assertEquals(
                Set.of(
                        "qr:abc123:300",
                        "qr:abc123:500"
                ),
                new HashSet<>(captor.getValue())
        );

        verify(stringRedisTemplate)
                .delete("qr:index:abc123");
    }

    @Test
    void shouldDoNothingWhenNoCachedVariantsExist() {

        when(setOperations.members("qr:index:abc123"))
                .thenReturn(Collections.emptySet());

        qrCacheService.delete("abc123");

        verify(qrRedisTemplate, never())
                .delete(any(Collection.class));

        verify(stringRedisTemplate, never())
                .delete(anyString());
    }

    @Test
    void shouldDoNothingWhenShortCodeBlankDuringDelete() {

        qrCacheService.delete("");

        verifyNoInteractions(setOperations);
    }

    @Test
    void shouldIgnoreRedisConnectionFailureDuringDelete() {

        when(setOperations.members(anyString()))
                .thenThrow(
                        new RedisConnectionFailureException("redis down")
                );

        assertDoesNotThrow(() ->
                qrCacheService.delete("abc123")
        );
    }

    @Test
    void shouldIgnoreUnexpectedRuntimeExceptionDuringDelete() {

        when(setOperations.members(anyString()))
                .thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() ->
                qrCacheService.delete("abc123")
        );
    }
}
