package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class ShortCodeGenerationException extends BaseException {

    private static final String DEFAULT_MESSAGE =
            "Unable to generate short URL. Please try again.";

    public ShortCodeGenerationException() {
        super(DEFAULT_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ShortCodeGenerationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
