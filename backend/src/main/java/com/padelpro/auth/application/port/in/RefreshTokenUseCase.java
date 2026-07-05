package com.padelpro.auth.application.port.in;

import com.padelpro.auth.application.dto.TokenPair;

/**
 * Inbound port — renew an access token from a valid refresh token, rotating the refresh token.
 *
 * <p>Implemented by {@link com.padelpro.auth.application.service.AuthService}.
 *
 * <p>Validates the raw refresh token against the stored SHA-256 hash, ensures it is neither
 * expired nor revoked, revokes the one used (rotation) and issues a brand-new refresh token
 * (fresh 7-day window) plus a new JWT access token (RN-AUTH-09).
 */
public interface RefreshTokenUseCase {

    /**
     * Rotate the given refresh token and issue a new access/refresh token pair.
     *
     * @param rawRefreshToken the raw refresh token read from the {@code refresh_token} cookie
     * @return a {@link TokenPair} with a new access token and the new raw refresh token
     * @throws com.padelpro.auth.domain.exception.RefreshTokenInvalidException
     *         if the token is missing, unknown, expired or revoked
     */
    TokenPair refresh(String rawRefreshToken);
}
