package com.padelpro.auth.application.dto;

/**
 * Internal service return value for a successful login.
 *
 * <p>Carries both the public {@link TokenPair} (sent in the response body)
 * and the raw refresh token (set as an HttpOnly cookie by the controller).
 * The raw refresh token is NEVER serialised to JSON (only the hash is persisted).
 */
public record LoginResult(
        TokenPair tokenPair,
        String rawRefreshToken
) {}
