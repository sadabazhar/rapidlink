package com.rapidlink.controller;

import com.rapidlink.dto.request.CreateShortUrlRequest;
import com.rapidlink.services.ShortUrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/v1/urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;

    // Creates a new short URL
    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody CreateShortUrlRequest request){
        String shortCode = service.createShortUrl(request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shortCode);
    }
}
