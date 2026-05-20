package com.rapidlink.unit.service;

import com.rapidlink.dto.response.analytics.AnalyticsOverviewResponse;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.repository.ClickEventRepository;
import com.rapidlink.repository.ShortUrlRepository;
import com.rapidlink.services.impl.AnalyticsQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @InjectMocks
    private AnalyticsQueryServiceImpl analyticsQueryService;

    private ShortUrl shortUrl;
    private UUID shortUrlId;

    @BeforeEach
    void setUp() {

        shortUrlId = UUID.randomUUID();

        shortUrl = ShortUrl.builder()
                .id(shortUrlId)
                .shortCode("abc123")
                .originalUrl("https://example.com")
                .clickCount(150L)
                .build();
    }

    @Test
    void shouldReturnAnalyticsOverviewSuccessfully() {

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        when(clickEventRepository.countDistinctVisitors(shortUrlId))
                .thenReturn(120L);

        when(clickEventRepository.getCountryBreakdown(shortUrlId))
                .thenReturn(List.of(
                        new Object[]{"IN", 80L},
                        new Object[]{"US", 40L}
                ));

        when(clickEventRepository.getDeviceBreakdown(shortUrlId))
                .thenReturn(List.of(
                        new Object[]{"Mobile", 90L},
                        new Object[]{"Desktop", 60L}
                ));

        when(clickEventRepository.getReferrerBreakdown(shortUrlId))
                .thenReturn(List.of(
                        new Object[]{"Google", 100L},
                        new Object[]{"Twitter", 50L}
                ));

        AnalyticsOverviewResponse response =
                analyticsQueryService.getOverview("abc123");

        assertNotNull(response);

        assertEquals(shortUrlId, response.shortUrlId());
        assertEquals(150L, response.totalClicks());
        assertEquals(120L, response.uniqueVisitors());

        assertEquals(80L, response.countryBreakdown().get("IN"));
        assertEquals(40L, response.countryBreakdown().get("US"));

        assertEquals(90L, response.deviceBreakdown().get("Mobile"));
        assertEquals(60L, response.deviceBreakdown().get("Desktop"));

        assertEquals(100L, response.referrerBreakdown().get("Google"));
        assertEquals(50L, response.referrerBreakdown().get("Twitter"));

        verify(shortUrlRepository).findByShortCode("abc123");
        verify(clickEventRepository).countDistinctVisitors(shortUrlId);
        verify(clickEventRepository).getCountryBreakdown(shortUrlId);
        verify(clickEventRepository).getDeviceBreakdown(shortUrlId);
        verify(clickEventRepository).getReferrerBreakdown(shortUrlId);
    }

    @Test
    void shouldThrowExceptionWhenShortUrlNotFound() {

        when(shortUrlRepository.findByShortCode("missing"))
                .thenReturn(Optional.empty());

        ShortUrlNotFoundException exception = assertThrows(
                ShortUrlNotFoundException.class,
                () -> analyticsQueryService.getOverview("missing")
        );

        assertEquals("Short URL not found", exception.getMessage());

        verify(shortUrlRepository).findByShortCode("missing");

        verifyNoInteractions(clickEventRepository);
    }

    @Test
    void shouldReturnEmptyBreakdownsWhenNoAnalyticsDataExists() {

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        when(clickEventRepository.countDistinctVisitors(shortUrlId))
                .thenReturn(0L);

        when(clickEventRepository.getCountryBreakdown(shortUrlId))
                .thenReturn(Collections.emptyList());

        when(clickEventRepository.getDeviceBreakdown(shortUrlId))
                .thenReturn(Collections.emptyList());

        when(clickEventRepository.getReferrerBreakdown(shortUrlId))
                .thenReturn(Collections.emptyList());

        AnalyticsOverviewResponse response =
                analyticsQueryService.getOverview("abc123");

        assertNotNull(response);

        assertEquals(150L, response.totalClicks());
        assertEquals(0L, response.uniqueVisitors());

        assertTrue(response.countryBreakdown().isEmpty());
        assertTrue(response.deviceBreakdown().isEmpty());
        assertTrue(response.referrerBreakdown().isEmpty());
    }

    @Test
    void shouldHandleUnknownCategoriesProperly() {

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        when(clickEventRepository.countDistinctVisitors(shortUrlId))
                .thenReturn(5L);

        when(clickEventRepository.getCountryBreakdown(shortUrlId))
                .thenReturn(Collections.singletonList(
                        new Object[]{"UNKNOWN", 3L}
                ));

        when(clickEventRepository.getDeviceBreakdown(shortUrlId))
                .thenReturn(Collections.singletonList(
                        new Object[]{"UNKNOWN", 2L}
                ));

        when(clickEventRepository.getReferrerBreakdown(shortUrlId))
                .thenReturn(Collections.singletonList(
                        new Object[]{"Direct", 5L}
                ));

        AnalyticsOverviewResponse response =
                analyticsQueryService.getOverview("abc123");

        assertEquals(3L, response.countryBreakdown().get("UNKNOWN"));
        assertEquals(2L, response.deviceBreakdown().get("UNKNOWN"));
        assertEquals(5L, response.referrerBreakdown().get("Direct"));
    }

    @Test
    void shouldCorrectlyTransformBreakdownListsToMaps() {

        when(shortUrlRepository.findByShortCode("abc123"))
                .thenReturn(Optional.of(shortUrl));

        when(clickEventRepository.countDistinctVisitors(shortUrlId))
                .thenReturn(10L);

        when(clickEventRepository.getCountryBreakdown(shortUrlId))
                .thenReturn(List.of(
                        new Object[]{"IN", 4L},
                        new Object[]{"US", 6L}
                ));

        when(clickEventRepository.getDeviceBreakdown(shortUrlId))
                .thenReturn(Collections.emptyList());

        when(clickEventRepository.getReferrerBreakdown(shortUrlId))
                .thenReturn(Collections.emptyList());

        AnalyticsOverviewResponse response =
                analyticsQueryService.getOverview("abc123");

        Map<String, Long> countryMap = response.countryBreakdown();

        assertEquals(2, countryMap.size());
        assertEquals(4L, countryMap.get("IN"));
        assertEquals(6L, countryMap.get("US"));
    }
}
