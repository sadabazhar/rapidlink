package com.rapidlink.unit.service;

import com.rapidlink.dto.request.analytics.ClickEventRequest;
import com.rapidlink.services.impl.ClickMetadataExtractorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class ClickMetadataExtractorTest {

    private ClickMetadataExtractorImpl extractor;

    @BeforeEach
    void setUp() {
        extractor = new ClickMetadataExtractorImpl();
    }

    @Test
    void shouldExtractMetadataUsingXForwardedForHeader() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) Mobile");
        request.addHeader("Referer", "https://google.com");
        request.setRemoteAddr("192.168.1.1");

        ClickEventRequest result = extractor.extract(shortUrlId, request);

        assertEquals(shortUrlId, result.shortUrlId());
        assertEquals("UNKNOWN", result.country());
        assertEquals("Mobile", result.deviceType());
        assertEquals("https://google.com", result.referrer());

        // Verify forwarded IP is used instead of remote address
        String expectedHash = sha256("203.0.113.10");
        assertEquals(expectedHash, result.ipHash());
    }

    @Test
    void shouldFallbackToRemoteAddrWhenForwardedHeaderMissing() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Referer", "https://twitter.com");

        ClickEventRequest result = extractor.extract(shortUrlId, request);

        String expectedHash = sha256("198.51.100.20");

        assertEquals(expectedHash, result.ipHash());
        assertEquals("Desktop", result.deviceType());
        assertEquals("https://twitter.com", result.referrer());
    }

    @Test
    void shouldReturnUnknownDeviceWhenUserAgentMissing() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");

        ClickEventRequest result = extractor.extract(shortUrlId, request);

        assertEquals("UNKNOWN", result.deviceType());
    }

    @Test
    void shouldDetectTabletDevice() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("User-Agent", "Mozilla Tablet Safari");

        ClickEventRequest result = extractor.extract(shortUrlId, request);

        assertEquals("Tablet", result.deviceType());
    }

    @Test
    void shouldDefaultReferrerToDirectWhenMissing() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("User-Agent", "Mozilla/5.0");

        ClickEventRequest result = extractor.extract(shortUrlId, request);

        assertEquals("Direct", result.referrer());
    }

    @Test
    void shouldHashSameIpConsistently() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("198.51.100.20");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("198.51.100.20");

        ClickEventRequest result1 = extractor.extract(shortUrlId, request1);
        ClickEventRequest result2 = extractor.extract(shortUrlId, request2);

        assertEquals(result1.ipHash(), result2.ipHash());
    }

    @Test
    void shouldProduceDifferentHashesForDifferentIps() {
        UUID shortUrlId = UUID.randomUUID();

        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("198.51.100.20");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("203.0.113.10");

        ClickEventRequest result1 = extractor.extract(shortUrlId, request1);
        ClickEventRequest result2 = extractor.extract(shortUrlId, request2);

        assertNotEquals(result1.ipHash(), result2.ipHash());
    }

    /**
     * Utility method to verify SHA-256 output
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}