package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class ShortCodeGenerationException extends BaseException{
    public ShortCodeGenerationException() {
        super("Unable to generate short URL. Please try again.", HttpStatus.CONFLICT);
    }
}
