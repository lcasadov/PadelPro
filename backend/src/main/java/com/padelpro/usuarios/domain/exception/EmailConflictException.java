package com.padelpro.usuarios.domain.exception;

/**
 * Thrown when an email address is already in use by another user.
 * Used during profile updates and admin user creation to signal a 409 conflict.
 */
public class EmailConflictException extends RuntimeException {

    public EmailConflictException() {
        super("Email address is already in use");
    }
}
