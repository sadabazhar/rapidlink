package com.rapidlink.api;

import com.rapidlink.controller.AnalyticsController;
import com.rapidlink.dto.response.analytics.AnalyticsOverviewResponse;
import com.rapidlink.services.AnalyticsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsQueryService analyticsQueryService;

    private AnalyticsOverviewResponse analyticsResponse;
    private UUID shortUrlId;


    @BeforeEach
    void setUp() {

        shortUrlId = UUID.randomUUID();

        analyticsResponse = new AnalyticsOverviewResponse(
                shortUrlId,
                150L,
                120L,
                Map.of(
                        "IN", 80L,
                        "US", 40L
                ),
                Map.of(
                        "Mobile", 90L,
                        "Desktop", 60L
                ),
                Map.of(
                        "Google", 100L,
                        "Twitter", 50L
                )
        );
    }

    @Test
    void shouldReturnAnalyticsOverviewSuccessfully() throws Exception {

        when(analyticsQueryService.getOverview("abc123"))
                .thenReturn(analyticsResponse);

        mockMvc.perform(
                        get("/api/analytics/abc123")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrlId")
                        .value(shortUrlId.toString()))
                .andExpect(jsonPath("$.totalClicks")
                        .value(150))
                .andExpect(jsonPath("$.uniqueVisitors")
                        .value(120))

                // Country Breakdown
                .andExpect(jsonPath("$.countryBreakdown.IN")
                        .value(80))
                .andExpect(jsonPath("$.countryBreakdown.US")
                        .value(40))

                // Device Breakdown
                .andExpect(jsonPath("$.deviceBreakdown.Mobile")
                        .value(90))
                .andExpect(jsonPath("$.deviceBreakdown.Desktop")
                        .value(60))

                // Referrer Breakdown
                .andExpect(jsonPath("$.referrerBreakdown.Google")
                        .value(100))
                .andExpect(jsonPath("$.referrerBreakdown.Twitter")
                        .value(50));

        verify(analyticsQueryService).getOverview("abc123");
    }

    @Test
    void shouldReturn404WhenShortUrlNotFound() throws Exception {

        when(analyticsQueryService.getOverview("missing"))
                .thenThrow(new IllegalArgumentException("Short URL not found"));

        mockMvc.perform(
                        get("/api/analytics/missing")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound());

        verify(analyticsQueryService).getOverview("missing");
    }

    @Test
    void shouldReturnEmptyAnalyticsBreakdowns() throws Exception {

        AnalyticsOverviewResponse emptyResponse =
                new AnalyticsOverviewResponse(
                        shortUrlId,
                        0L,
                        0L,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap()
                );

        when(analyticsQueryService.getOverview("empty123"))
                .thenReturn(emptyResponse);

        mockMvc.perform(
                        get("/api/analytics/empty123")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks")
                        .value(0))
                .andExpect(jsonPath("$.uniqueVisitors")
                        .value(0))
                .andExpect(jsonPath("$.countryBreakdown")
                        .isEmpty())
                .andExpect(jsonPath("$.deviceBreakdown")
                        .isEmpty())
                .andExpect(jsonPath("$.referrerBreakdown")
                        .isEmpty());

        verify(analyticsQueryService).getOverview("empty123");
    }

    @Test
    void shouldHandleSpecialCharactersInShortCode() throws Exception {

        when(analyticsQueryService.getOverview("abc-123_xyz"))
                .thenReturn(analyticsResponse);

        mockMvc.perform(
                        get("/api/analytics/abc-123_xyz")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());

        verify(analyticsQueryService)
                .getOverview("abc-123_xyz");
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedExceptions() throws Exception {

        when(analyticsQueryService.getOverview("abc123"))
                .thenThrow(new RuntimeException("Unexpected failure"));

        mockMvc.perform(
                        get("/api/analytics/abc123")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isInternalServerError());

        verify(analyticsQueryService).getOverview("abc123");
    }
}
