package com.padelpro.auth.domain.exception;

/**
 * Thrown when a request parameter violates a domain validation rule
 * (e.g. page size exceeds the allowed maximum).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
