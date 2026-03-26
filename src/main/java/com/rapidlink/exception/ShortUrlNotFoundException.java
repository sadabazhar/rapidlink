package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class ShortUrlNotFoundException extends BaseException{
    public ShortUrlNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
