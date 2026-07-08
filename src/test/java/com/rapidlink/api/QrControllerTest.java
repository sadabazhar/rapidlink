package com.rapidlink.api;

import com.rapidlink.config.RapidLinkProperties;
import com.rapidlink.controller.QrController;
import com.rapidlink.metrics.RapidLinkMetrics;
import com.rapidlink.security.JwtAuthenticationFilter;
import com.rapidlink.services.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QrController.class)
@AutoConfigureMockMvc(addFilters = false)
class QrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QrCodeService qrCodeService;

    @MockitoBean
    private RapidLinkProperties rapidLinkProperties;

    @MockitoBean
    private RapidLinkMetrics rapidLinkMetrics;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {

        RapidLinkProperties.Qr qrProperties = mock(RapidLinkProperties.Qr.class);

        when(rapidLinkProperties.getQr())
                .thenReturn(qrProperties);

        when(qrProperties.getDefaultSize())
                .thenReturn(300);

        when(qrProperties.getFormat())
                .thenReturn("PNG");
    }

    @Test
    void shouldGenerateQrUsingDefaultSize() throws Exception {

        byte[] qrBytes = "test".getBytes();

        when(qrCodeService.getQrCode("abc123", 300))
                .thenReturn(qrBytes);

        mockMvc.perform(get("/api/qr/abc123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qr-abc123.png\""
                ))
                .andExpect(content().bytes(qrBytes));

        verify(qrCodeService)
                .getQrCode("abc123", 300);
    }

    @Test
    void shouldGenerateQrUsingProvidedSize() throws Exception {

        byte[] qrBytes = "test".getBytes();

        when(qrCodeService.getQrCode("abc123", 500))
                .thenReturn(qrBytes);

        mockMvc.perform(
                        get("/api/qr/abc123")
                                .param("size", "500")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(qrBytes));

        verify(qrCodeService)
                .getQrCode("abc123", 500);
    }

    @Test
    void shouldReturnCacheHeaders() throws Exception {

        when(qrCodeService.getQrCode("abc123", 300))
                .thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/qr/abc123"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("max-age")
                ));

        verify(qrCodeService)
                .getQrCode("abc123", 300);
    }

    @Test
    void shouldUseDefaultSizeWhenSizeParameterMissing() throws Exception {

        when(qrCodeService.getQrCode("abc123", 300))
                .thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/qr/abc123"))
                .andExpect(status().isOk());

        verify(qrCodeService)
                .getQrCode("abc123", 300);
    }
}