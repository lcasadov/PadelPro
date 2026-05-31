package com.padelpro.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body returned by {@link GlobalExceptionHandler}.
 *
 * <p>The {@code details} field is omitted from the JSON when null
 * (e.g. it is only populated for {@code INVALID_PASSWORD} errors that carry violation codes).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Instant timestamp,
        List<String> details
) {
    /** Convenience constructor for errors without a details list. */
    public ErrorResponse(String error, String message) {
        this(error, message, Instant.now(), null);
    }

    /** Convenience constructor for errors with a violation-code detail list. */
    public ErrorResponse(String error, String message, List<String> details) {
        this(error, message, Instant.now(), details);
    }
}
