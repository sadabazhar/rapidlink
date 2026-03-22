package com.rapidlink.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class Health {

    @GetMapping
    public ResponseEntity<String> health(){
        return ResponseEntity.ok("Ok");
    }
}
