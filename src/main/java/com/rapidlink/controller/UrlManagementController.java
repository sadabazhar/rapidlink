package com.rapidlink.controller;

import com.rapidlink.dto.request.CreateShortUrlRequest;
import com.rapidlink.dto.response.CreatedShortUrlResponse;
import com.rapidlink.services.UrlManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles URL lifecycle operations such as creating short URLs.
 */
@RestController
@RequestMapping("api/v1/urls")
@RequiredArgsConstructor
public class UrlManagementController {

    private final UrlManagementService service;

    // Creates a new short URL
    @PostMapping
    public ResponseEntity<CreatedShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request){
        String shortCode = service.createUrl(request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedShortUrlResponse(shortCode));
    }
}
