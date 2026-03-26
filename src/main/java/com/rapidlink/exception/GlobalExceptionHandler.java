package com.rapidlink.exception;

import com.rapidlink.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles all exceptions across the application.
 * Converts exceptions into a consistent API error response.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles all custom exceptions (business logic errors).
     * Example: URL not found, expired, deactivated, etc.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException ex,
            HttpServletRequest req
    ) {
        HttpStatus status = ex.getStatus();

        // Log as WARN since these are expected errors (not system failures)
        log.warn("Handled business exception: status={}, message={}, path={}",
                status.value(), ex.getMessage(), req.getRequestURI());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                req.getRequestURI()
        );

        return new ResponseEntity<>(response, status);
    }

    /**
     * Handles validation errors from @Valid requests.
     * Returns all validation messages instead of just one.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());

        // Combine all errors into one message (simple approach for now)
        String message = String.join(", ", errors);

        log.warn("Validation failed at path={} errors={}", req.getRequestURI(), errors);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                req.getRequestURI()
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles unexpected errors (fallback).
     * We don’t expose internal details to the user.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOtherExceptions(
            Exception ex,
            HttpServletRequest req
    ) {
        // Log full error for debugging
        log.error("Unexpected error occurred at path={}", req.getRequestURI(), ex);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong",
                req.getRequestURI()
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /*
     * -------- Future Improvements --------
     *
     * TODO: Remove HttpStatus from BaseException
     * Right now exceptions depend on HTTP, which is not ideal.
     * This will cause issues if we use Kafka, async jobs, etc.
     * Later, we should map exceptions to status here instead.
     *
     * TODO: Improve ErrorResponse structure
     * Add:
     * - errorCode (e.g., RL_404)
     * - traceId (for debugging)
     * - validationErrors (list instead of single message)
     */
}