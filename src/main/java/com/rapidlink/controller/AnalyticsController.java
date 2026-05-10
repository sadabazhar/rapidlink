package com.rapidlink.controller;


import com.rapidlink.dto.response.analytics.AnalyticsOverviewResponse;
import com.rapidlink.services.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<AnalyticsOverviewResponse> getAnalyticsOverview(
            @PathVariable String shortCode) {

        AnalyticsOverviewResponse response =
                analyticsQueryService.getOverview(shortCode);

        return ResponseEntity.ok(response);
    }
}
