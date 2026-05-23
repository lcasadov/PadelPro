package com.padelpro.auth.application.dto;

/**
 * Response payload returned after a successful login.
 * The refresh token is delivered via an HttpOnly cookie — it is NOT included here.
 *
 * @param accessToken  signed JWT access token
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    access token lifetime in seconds
 */
public record TokenPair(
        String accessToken,
        String tokenType,
        int expiresIn
) {}
