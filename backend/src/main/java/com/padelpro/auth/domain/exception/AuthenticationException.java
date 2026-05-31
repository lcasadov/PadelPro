package com.padelpro.auth.domain.exception;

/**
 * Thrown when authentication fails due to invalid credentials.
 *
 * <p>Used for BOTH unknown email and wrong password cases to prevent
 * user enumeration (anti-enumeration / RN-AUTH-06).
 * Maps to HTTP 401 Unauthorized with body {@code {error: "AUTH_INVALID_CREDENTIALS"}}.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException() {
        super("AUTH_INVALID_CREDENTIALS");
    }
}
