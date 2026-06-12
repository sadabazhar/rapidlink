package com.rapidlink.exception;

import org.springframework.http.HttpStatus;

public class QrGenerationException extends BaseException {

    private static final String DEFAULT_MESSAGE =
            "Unable to generate QR for shortcode. Please try again.";

    public QrGenerationException() {
        super(DEFAULT_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public QrGenerationException(String message) {super(message, HttpStatus.INTERNAL_SERVER_ERROR);}
}
