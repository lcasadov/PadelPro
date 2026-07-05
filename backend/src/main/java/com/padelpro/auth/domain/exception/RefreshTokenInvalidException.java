package com.padelpro.auth.domain.exception;

/**
 * Thrown when a refresh token presented to {@code POST /api/auth/refresh} is missing,
 * unknown, expired or already revoked.
 *
 * <p>Maps to HTTP 401 Unauthorized with body {@code {error: "AUTH_REFRESH_INVALID"}}.
 * The raw token and its hash are never included in the message (RN-RGPD-04).
 */
public class RefreshTokenInvalidException extends RuntimeException {

    public RefreshTokenInvalidException() {
        super("AUTH_REFRESH_INVALID");
    }
}
