package com.padelpro.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned after a successful login.
 *
 * <p>The {@code rawRefreshToken} is NOT serialised to JSON ({@code @JsonIgnore}).
 * It is used only by the controller to set the HttpOnly refresh-token cookie.
 * The hash of the refresh token is persisted in the database (never the raw value).
 *
 * @param accessToken         signed JWT access token
 * @param tokenType           always {@code "Bearer"}
 * @param expiresIn           access token lifetime in seconds
 * @param mustChangePassword  whether the user must set a new password before normal use (D9)
 * @param rawRefreshToken     raw refresh token for cookie — NOT included in JSON response
 */
public record TokenPair(
        @JsonProperty("access_token")          String accessToken,
        @JsonProperty("token_type")            String tokenType,
        @JsonProperty("expires_in")            int expiresIn,
        @JsonProperty("must_change_password")  boolean mustChangePassword,
        @JsonIgnore                             String rawRefreshToken
) {

    /**
     * Convenience constructor for tests and callers that don't need the refresh token
     * (defaults {@code mustChangePassword} to {@code false}).
     */
    public TokenPair(String accessToken, String tokenType, int expiresIn) {
        this(accessToken, tokenType, expiresIn, false, null);
    }
}
