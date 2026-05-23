package com.padelpro.auth.application.service;

import com.padelpro.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

/**
 * Service responsible for JWT generation and validation.
 *
 * <p><strong>Wave 2 skeleton</strong> — no business logic yet.
 * Full implementation (HS256 signing with configurable secret, claims population,
 * expiry validation, token blacklist check) will be added in Wave 3.
 */
@Service
public class JwtService {

    /**
     * Generate a signed JWT access token for the given user.
     *
     * @param user the authenticated user
     * @return a signed JWT string
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    public String generateAccessToken(User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Validate a JWT string and extract its claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return parsed {@link Claims} if the token is valid and not expired
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    public Claims validateToken(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
