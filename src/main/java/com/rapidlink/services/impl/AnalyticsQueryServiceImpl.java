package com.rapidlink.services.impl;

import com.rapidlink.dto.response.analytics.AnalyticsOverviewResponse;
import com.rapidlink.entity.ShortUrl;
import com.rapidlink.exception.ShortUrlNotFoundException;
import com.rapidlink.repository.ClickEventRepository;
import com.rapidlink.services.AnalyticsQueryService;
import com.rapidlink.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;

    @Override
    public AnalyticsOverviewResponse getOverview(String shortCode) {

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ShortUrlNotFoundException("Short URL not found"));

        UUID shortUrlId = shortUrl.getId();

        long totalClicks = shortUrl.getClickCount();

        long uniqueVisitors =
                clickEventRepository.countDistinctVisitors(shortUrlId);

        Map<String, Long> countryBreakdown =
                toBreakdownMap(
                        clickEventRepository.getCountryBreakdown(shortUrlId)
                );

        Map<String, Long> deviceBreakdown =
                toBreakdownMap(
                        clickEventRepository.getDeviceBreakdown(shortUrlId)
                );

        Map<String, Long> referrerBreakdown =
                toBreakdownMap(
                        clickEventRepository.getReferrerBreakdown(shortUrlId)
                );

        return new AnalyticsOverviewResponse(
                shortUrlId,
                totalClicks,
                uniqueVisitors,
                countryBreakdown,
                deviceBreakdown,
                referrerBreakdown
        );
    }

    private Map<String, Long> toBreakdownMap(List<Object[]> results) {
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }
}
