package com.rapidlink.controller;

import com.rapidlink.services.ShortUrlService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@AllArgsConstructor
public class RedirectController {
    private final ShortUrlService service;

    //Redirects user to original URL using short code
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        URI uri = service.resolveUrl(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(uri)
                .build();
    }
}
