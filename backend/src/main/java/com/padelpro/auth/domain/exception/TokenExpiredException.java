package com.padelpro.auth.domain.exception;

/**
 * Thrown when a JWT token is valid in structure but has passed its expiry time.
 * Maps to HTTP 401 with body {@code {error: "TOKEN_EXPIRED"}}.
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED");
    }

    public TokenExpiredException(Throwable cause) {
        super("TOKEN_EXPIRED", cause);
    }
}
