package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class UrlExpiredException extends BaseException{
    public UrlExpiredException(String message) {
        super(message, HttpStatus.GONE);
    }
}
