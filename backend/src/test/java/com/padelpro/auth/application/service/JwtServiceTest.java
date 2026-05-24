package com.padelpro.auth.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-023 — Unit tests for JwtService.
 *
 * <p>TDD RED phase: all tests should FAIL with UnsupportedOperationException
 * until Wave 3 implements the actual JWT logic.
 *
 * <p>Scenarios covered: R-3.1 through R-3.3.
 * No Spring context — JwtService is instantiated directly.
 */
class JwtServiceTest {

    // JwtService has no dependencies that need mocking — it uses configuration
    // values injected by Spring in production. For unit tests we instantiate it
    // directly; Wave 3 must expose a constructor that accepts the secret and
    // expiry duration so tests can control them without a Spring context.
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        testUser = new User(
                "testuser",
                "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/lHGYSL5m2",
                "Test",
                "User",
                "test@example.com",
                UserRole.USER,
                UserStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    // -------------------------------------------------------------------------
    // R-3.1 — Valid token generation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-3.1: should generate valid token with correct claims")
    void should_generate_valid_token_with_correct_claims() {
        // Act
        String token = jwtService.generateAccessToken(testUser);

        // Assert
        assertThat(token)
                .isNotBlank()
                .contains(".")  // JWT has 3 dot-separated parts
                .satisfies(t -> assertThat(t.split("\\.")).hasSize(3));
    }

    @Test
    @DisplayName("R-3.1: token should contain user_id, role and expiry claims")
    void token_should_contain_user_id_role_and_expiry_claims() {
        // Act
        String token = jwtService.generateAccessToken(testUser);
        Claims claims = jwtService.validateToken(token);

        // Assert
        assertThat(claims.getSubject())
                .as("sub claim must be the user id or email")
                .isNotBlank();
        assertThat(claims.get("role", String.class))
                .as("role claim must be present")
                .isEqualTo("USER");
        assertThat(claims.getExpiration())
                .as("exp claim must be in the future")
                .isAfter(new Date());
    }

    @Test
    @DisplayName("R-3.1: should return true when token is valid and not expired")
    void should_return_true_when_token_is_valid_and_not_expired() {
        // Arrange
        String token = jwtService.generateAccessToken(testUser);

        // Act
        Claims claims = jwtService.validateToken(token);

        // Assert — validateToken must not throw and must return non-null Claims
        assertThat(claims).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    // -------------------------------------------------------------------------
    // R-3.2 — Expired token is rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-3.2: should throw exception when token is expired")
    void should_throw_exception_when_token_is_expired() {
        // Arrange — a pre-built token that expired at 2020-01-01T00:00:00Z
        // eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE1Nzc4MzYwMDAsImV4cCI6MTU3NzgzNjkwMH0.XmockSignatureXXXXXXXXXXXXXXXXXXXXXXXXX
        // Note: we use a crafted expired JWT string (exp = past). Wave 3's implementation
        // must sign with the configured secret; here we test the expiry-check path.
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE1Nzc4MzYwMDAsImV4cCI6MTU3NzgzNjkwMH0"
                + ".INVALID_SIGNATURE_FOR_EXPIRED_TOKEN_TEST";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.validateToken(expiredToken))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("TOKEN_EXPIRED")
                        .withFailMessage("Expired token must throw exception with TOKEN_EXPIRED message"));
    }

    // -------------------------------------------------------------------------
    // R-3.3 — Tampered token is rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-3.3: should throw exception when token signature is tampered")
    void should_throw_exception_when_token_signature_is_tampered() {
        // Arrange — generate a valid token, then tamper with the signature segment
        String validToken = jwtService.generateAccessToken(testUser);
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".TAMPERED_SIGNATURE_XXXX";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.validateToken(tamperedToken))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("TOKEN_INVALID")
                        .withFailMessage("Tampered token must throw exception with TOKEN_INVALID message"));
    }
}
