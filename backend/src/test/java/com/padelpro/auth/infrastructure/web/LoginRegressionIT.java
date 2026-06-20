package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.RefreshTokenRepository;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression IT for Issue #161 — login was broken because
 * {@code RefreshTokenRepository} declared a {@code default save(RefreshToken)} that threw
 * {@code UnsupportedOperationException}. Spring Data does NOT back interface {@code default}
 * methods, so the throwing stub stayed active and {@code AuthService.login} blew up at
 * {@code refreshTokenRepository.save(...)} → every authentication returned 500 / 401.
 *
 * <p>This test exercises {@code POST /api/auth/login} end-to-end against the <b>real Spring Data
 * JPA proxy</b> and a real PostgreSQL (Mockito is deliberately NOT used — a mock would hide
 * exactly this class of bug). A green run proves Spring Data provides the concrete {@code save}
 * implementation and the refresh token is persisted.
 *
 * <p>Extends {@link PostgresIntegrationTest}: connects to an external PostgreSQL via
 * {@code -Dit.postgres.url=...} (portable :5433 IT DB) or Testcontainers in CI. No Docker
 * dependency when the external DB is provided.
 */
@DisplayName("IT — login regression #161 (real Spring Data proxy, no Mockito)")
class LoginRegressionIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private static final String EMAIL = "login.regression@example.com";
    private static final String PASSWORD = "Password1";

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        userRepository.saveAndFlush(new User(
                "login.regression", passwordEncoder.encode(PASSWORD), "Login", "Regression",
                EMAIL, UserRole.USER, UserStatus.ACTIVE, now, now));
    }

    private String loginJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD));
    }

    @Test
    @DisplayName("#161: POST /api/auth/login returns 200 with a JWT access_token and persists a refresh token")
    void login_returns_200_with_jwt_and_persists_refresh_token() throws Exception {
        long refreshTokensBefore = refreshTokenRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                // The bug made this 500/401; the fix restores 200.
                .andExpect(status().isOk())
                // access_token must be a non-null, well-formed JWT (header.payload.signature).
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.access_token",
                        matchesPattern("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.expires_in").isNumber())
                // refresh token is delivered as an HttpOnly cookie (RN-AUTH-10).
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth/refresh")));

        // The refresh token reached the database through the real Spring Data proxy — the exact
        // path the throwing default save() used to break (#161).
        assertThat(refreshTokenRepository.count())
                .as("AuthService.login must persist exactly one refresh token via Spring Data")
                .isEqualTo(refreshTokensBefore + 1);
    }
}
