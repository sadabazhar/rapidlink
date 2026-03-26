package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class InvalidStoredUrlException extends BaseException {
    public InvalidStoredUrlException(String shortCode) {
        super("Invalid URL stored for shortcode: " + shortCode,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
