package com.padelpro.auth.domain.exception;

/**
 * Thrown when a registration is attempted with an email that is already in use.
 * Maps to HTTP 409 Conflict.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("EMAIL_ALREADY_REGISTERED: " + email);
    }
}
