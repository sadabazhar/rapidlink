package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class UrlDeactivatedException extends BaseException{
    public UrlDeactivatedException(String message) {
        super(message, HttpStatus.GONE);
    }
}
