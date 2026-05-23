package com.padelpro.auth.application.service;

import com.padelpro.auth.domain.exception.TokenExpiredException;
import com.padelpro.auth.domain.exception.TokenInvalidException;
import com.padelpro.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * Service responsible for JWT generation and validation.
 *
 * <p>Algorithm: HS256. Claims: {@code sub} (user id as String), {@code role}, {@code iat}, {@code exp}.
 * Token lifetime: 15 minutes (900 seconds) by default, configurable via
 * {@code app.jwt.access-token-expiry-minutes}.
 *
 * <p>The class is Spring-managed ({@code @Service}) but can also be instantiated directly
 * in unit tests — Spring {@code @Value} fields fall back to their defaults when the Spring
 * context is absent (the fields are set via the constructor / Spring injection).
 */
@Service
public class JwtService {

    // Default secret must be at least 32 chars (256 bits) for HS256.
    // In tests, Spring is absent so @Value is never processed — we rely on the field
    // initializer default. The default is intentionally long enough for HS256.
    private String secret = "change-me-in-production-min-32-chars!!-padelpro-jwt-secret";

    private int expiryMinutes = 15;

    // Spring injects these when the service is a Spring bean
    @Value("${app.jwt.secret:change-me-in-production-min-32-chars!!-padelpro-jwt-secret}")
    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Value("${app.jwt.access-token-expiry-minutes:15}")
    public void setExpiryMinutes(int expiryMinutes) {
        this.expiryMinutes = expiryMinutes;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the access-token lifetime in seconds, derived from the configured
     * {@code app.jwt.access-token-expiry-minutes} property.
     *
     * <p>Used by {@link AuthService} to populate the {@code expires_in} field of
     * {@link com.padelpro.auth.application.dto.TokenPair} so that the response
     * value always matches the actual JWT expiry.
     *
     * @return expiry duration in seconds
     */
    public int getExpirySeconds() {
        return expiryMinutes * 60;
    }

    /**
     * Generate a signed JWT access token for the given user.
     *
     * @param user the authenticated user
     * @return a signed HS256 JWT string
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (long) expiryMinutes * 60 * 1000);

        return Jwts.builder()
                .subject(user.getId() != null ? user.getId().toString() : user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate a JWT string and extract its claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return parsed {@link Claims} if the token is valid and not expired
     * @throws TokenExpiredException if the token has passed its expiry time
     * @throws TokenInvalidException if the token signature is invalid or malformed
     */
    public Claims validateToken(String token) {
        // Check expiry first by inspecting the JWT body without signature verification.
        // JJWT validates the signature before expiry, so a tampered-but-expired token
        // would throw JwtException (bad sig) before ExpiredJwtException.
        // We detect expiry independently so the correct domain exception is raised.
        checkExpiry(token);

        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException(e);
        } catch (JwtException e) {
            throw new TokenInvalidException(e);
        } catch (IllegalArgumentException e) {
            throw new TokenInvalidException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Inspects the JWT payload (without signature verification) and throws
     * {@link TokenExpiredException} if the {@code exp} claim is in the past.
     *
     * <p>This pre-check is necessary because JJWT 0.12.x verifies the signature
     * before checking expiry. A token that is both expired AND tampered would
     * otherwise surface as {@code TOKEN_INVALID} instead of {@code TOKEN_EXPIRED}.
     * Checking expiry first gives callers the more precise error code.
     */
    private void checkExpiry(String token) {
        try {
            // JWT structure: header.payload.signature — split and decode payload
            String[] parts = token.split("\\.");
            if (parts.length < 2) return; // malformed — let parseSignedClaims report it

            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            // Extract "exp":<number> with a simple regex — avoids pulling in a JSON lib
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("\"exp\"\\s*:\\s*(\\d+)").matcher(payload);
            if (m.find()) {
                long expEpochSeconds = Long.parseLong(m.group(1));
                if (expEpochSeconds < System.currentTimeMillis() / 1000L) {
                    throw new TokenExpiredException();
                }
            }
        } catch (TokenExpiredException e) {
            throw e;
        } catch (Exception e) {
            // Any decode/parse error here means the token is malformed;
            // let parseSignedClaims handle it and throw TokenInvalidException.
        }
    }

    /** Pads a Base64url string to a multiple of 4 characters as required by the decoder. */
    private static String padBase64(String base64url) {
        int mod = base64url.length() % 4;
        if (mod == 0) return base64url;
        return base64url + "=".repeat(4 - mod);
    }

    private SecretKey signingKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
