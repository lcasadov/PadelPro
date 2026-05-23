package com.padelpro.auth.domain.exception;

/**
 * Thrown when a JWT token has an invalid or tampered signature.
 * Maps to HTTP 401 with body {@code {error: "TOKEN_INVALID"}}.
 */
public class TokenInvalidException extends RuntimeException {

    public TokenInvalidException() {
        super("TOKEN_INVALID");
    }

    public TokenInvalidException(Throwable cause) {
        super("TOKEN_INVALID", cause);
    }
}
