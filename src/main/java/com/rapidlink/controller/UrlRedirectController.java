package com.rapidlink.controller;

import com.rapidlink.services.UrlRedirectService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Handles redirection from short codes to original URLs.
 */
@RestController
@AllArgsConstructor
public class UrlRedirectController {

    private final UrlRedirectService service;

    //Redirects user to original URL using short code
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        URI uri = service.getRedirectUrl(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(uri)
                .build();
    }
}
