package com.rapidlink.unit.validator;

import com.rapidlink.exception.BadRequestException;
import com.rapidlink.validation.UrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidationTest {

    // Test: Accept Valid http url
    @Test
    void shouldNotThrowException_whenValidHttpUrlProvided() {

        String url = "http://example.com";

        // Expectation:
        // No exception should be thrown for valid input
        assertDoesNotThrow(() -> UrlValidator.validate(url));
    }

    // Test: Accept Valid https url
    @Test
    void shouldNotThrowException_whenValidHttpsUrlProvided() {

        String url = "https://google.com";

        // Expectation:
        // HTTPS URLs are allowed → no exception
        assertDoesNotThrow(() -> UrlValidator.validate(url));
    }

    // Test: Invalid url not accepted
    @ParameterizedTest
    @ValueSource(strings = {"ftp://abc.com", "file://test", "ws://socket"})
    void shouldRejectUnsupportedSchemes(String url) {
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(url));
    }

    // Test: Reject missing host url request
    @Test
    void shouldThrowBadRequestException_whenHostIsMissing() {

        String url = "http:///path-only";

        // Expectation:
        // Invalid structure → should throw exception
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(url));
    }

    // Test: Reject malformed url
    @Test
    void shouldThrowBadRequestException_whenUrlIsMalformed() {

        String url = "ht!tp://bad-url";

        // Expectation:
        // Parsing/validation should fail
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(url));
    }

    // Test: Reject null url
    @Test
    void shouldThrowBadRequestException_whenUrlIsNull() {

        // Null input (edge case)
        // Expectation:
        // Service must handle null safely and reject it
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(null));
    }

    // Test: Reject empty url
    @Test
    void shouldThrowBadRequestException_whenUrlIsBlank() {

        // Blank string input
        // Expectation:
        // Blank/empty URLs are invalid
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(" "));
    }

    // Test: Reject very long url
    @Test
    void shouldThrowBadRequestException_whenUrlIsTooLong() {

        // Create an excessively long URL (beyond allowed limit)
        String longUrl = "http://" + "a".repeat(3000) + ".com";

        // Expectation:
        // Service should enforce max length constraint
        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(longUrl));
    }

    // Test: Reject space contain url
    @Test
    void shouldThrowBadRequestException_whenUrlContainsSpaces() {

        String url = "http://google .com";

        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(url));
    }

    // Test: Reject protocol missing url
    @Test
    void shouldThrowBadRequestException_whenUrlHasNoProtocol() {

        String url = "google.com";

        assertThrows(BadRequestException.class,
                () -> UrlValidator.validate(url));
    }

    // Test: Accept url with port
    @Test
    void shouldAcceptUrlWithPort() {
        assertDoesNotThrow(() ->
                UrlValidator.validate("http://localhost:8080"));
    }

    // Test: Accept url with query params
    @Test
    void shouldAcceptUrlWithQueryParams() {
        assertDoesNotThrow(() ->
                UrlValidator.validate("https://example.com/search?q=test"));
    }

    // Test: Accept url with fragment
    @Test
    void shouldAcceptUrlWithFragment() {
        assertDoesNotThrow(() ->
                UrlValidator.validate("https://example.com/page#section"));
    }
}
