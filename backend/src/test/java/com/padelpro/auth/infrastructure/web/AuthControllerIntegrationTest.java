package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T-024 — Integration tests for AuthController against a real PostgreSQL 15.
 *
 * <p>Extends {@link PostgresIntegrationTest} so it runs both locally (Vía A external
 * Postgres on :5433) and in CI (Testcontainers). Replaces the previous direct
 * {@code @Testcontainers} setup, which could not run on hosts where the Docker Desktop
 * named pipe breaks the docker-java client.
 *
 * <p>Scenarios covered: R-1.1 to R-1.4 (register) and R-2.1 to R-2.4 (login).
 */
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * A unique client IP per test method. The RateLimitFilter keys its (singleton, context-cached)
     * buckets by client IP, so without a fresh IP per method the 5 login / 3 register per-minute
     * limits would leak between methods (and across IT classes sharing the Spring context),
     * making register/login return 429 instead of the asserted status. Each method here makes few
     * enough auth calls to stay under the limit on its own IP.
     */
    private String clientIp;

    @BeforeEach
    void assignUniqueClientIp() {
        clientIp = "10." + (int) (Math.random() * 254 + 1) + "."
                + (int) (Math.random() * 254 + 1) + "." + (int) (Math.random() * 254 + 1);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    /** POST builder for an auth endpoint, tagged with this test's unique client IP. */
    private MockHttpServletRequestBuilder authPost(String path, String body) {
        return post(path)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Map<String, String> registerBody(String firstName, String lastName,
                                              String email, String password) {
        return Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "email", email,
                "password", password
        );
    }

    private Map<String, String> loginBody(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    // =========================================================================
    // REGISTER — R-1.x
    // =========================================================================

    @Test
    @DisplayName("R-1.1: register should return 201 with user id, email and role when valid")
    void register_should_return_201_with_user_id_email_and_role_when_valid() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Alice", "Smith", "alice@example.com", "Password1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("R-1.2: register should return 409 when email already exists")
    void register_should_return_409_when_email_already_exists() throws Exception {
        // First registration succeeds
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Alice", "Smith", "dup@example.com", "Password1"))))
                .andExpect(status().isCreated());

        // Second registration with same email → 409
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Alice2", "Smith", "dup@example.com", "Password1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("R-1.3a: register should return 400 when password is too short")
    void register_should_return_400_when_password_is_too_short() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Bob", "Jones", "bob@example.com", "Pass1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.details", hasItem("MIN_LENGTH_8")));
    }

    @Test
    @DisplayName("R-1.3b: register should return 400 when password has no uppercase")
    void register_should_return_400_when_password_has_no_uppercase() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Carol", "White", "carol@example.com", "password1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.details", hasItem("REQUIRES_UPPERCASE")));
    }

    @Test
    @DisplayName("R-1.3c: register should return 400 when password has no number")
    void register_should_return_400_when_password_has_no_number() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Dave", "Brown", "dave@example.com", "PasswordNoDigit"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.details", hasItem("REQUIRES_NUMBER")));
    }

    @Test
    @DisplayName("R-1.4: register should return 400 when required field is missing")
    void register_should_return_400_when_required_field_is_missing() throws Exception {
        // firstName is absent
        String bodyMissingFirstName = "{\"lastName\":\"Smith\",\"email\":\"frank@example.com\",\"password\":\"Password1\"}";

        mockMvc.perform(authPost("/api/auth/register", bodyMissingFirstName))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // LOGIN — R-2.x
    // =========================================================================

    /**
     * Registers a user (created PENDING by RegistrationService) and then flips its status to
     * ACTIVE directly via JDBC. Login requires an ACTIVE account (RN-AUTH-08 → 403
     * {@code ACCOUNT_NOT_ACTIVE} otherwise), and the self-service activation flow is not part
     * of this suite, so we activate the row out-of-band to set up the login scenarios.
     */
    private void registerAndActivateUser(String email, String password) throws Exception {
        // Register — creates PENDING user
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Test", "User", email, password))))
                .andExpect(status().isCreated());

        // Activate via JDBC so the login path sees an ACTIVE account
        int updated = jdbcTemplate.update(
                "UPDATE users SET status = 'ACTIVE' WHERE email = ?", email);
        org.assertj.core.api.Assertions.assertThat(updated)
                .as("exactly one user row should be activated for %s", email)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("R-2.1: login should return 200 with access_token and refresh cookie when valid")
    void login_should_return_200_with_access_token_and_refresh_cookie_when_valid() throws Exception {
        // Arrange — register and activate user (relies on register being implemented)
        registerAndActivateUser("user@example.com", "Password1");

        // Act & Assert
        mockMvc.perform(authPost("/api/auth/login", json(loginBody("user@example.com", "Password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    @DisplayName("R-2.2: login should return 401 when email is not found")
    void login_should_return_401_when_email_not_found() throws Exception {
        mockMvc.perform(authPost("/api/auth/login", json(loginBody("nobody@example.com", "Password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("R-2.3: login should return 401 when password is wrong")
    void login_should_return_401_when_password_is_wrong() throws Exception {
        // Arrange — register a user first
        registerAndActivateUser("wrongpw@example.com", "Password1");

        // Act & Assert — wrong password
        mockMvc.perform(authPost("/api/auth/login", json(loginBody("wrongpw@example.com", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("R-2.2 + R-2.3: login error is same for missing email and wrong password (anti-enumeration)")
    void login_should_return_same_error_for_missing_email_and_wrong_password() throws Exception {
        // Register one user
        registerAndActivateUser("anti@example.com", "Password1");

        // Unknown email
        var unknownResult = mockMvc.perform(authPost("/api/auth/login", json(loginBody("unknown@example.com", "Password1"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Wrong password for known email
        var wrongPwResult = mockMvc.perform(authPost("/api/auth/login", json(loginBody("anti@example.com", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Response bodies must be identical for anti-enumeration purposes, EXCEPT the
        // volatile "timestamp" field (which legitimately differs between the two calls).
        // Compare the JSON trees with "timestamp" removed.
        ObjectNode unknownJson = (ObjectNode) objectMapper.readTree(
                unknownResult.getResponse().getContentAsString());
        ObjectNode wrongPwJson = (ObjectNode) objectMapper.readTree(
                wrongPwResult.getResponse().getContentAsString());
        unknownJson.remove("timestamp");
        wrongPwJson.remove("timestamp");

        org.assertj.core.api.Assertions.assertThat(unknownJson)
                .as("login error body (ignoring timestamp) must be identical for unknown email and wrong password")
                .isEqualTo(wrongPwJson);
    }

    // =========================================================================
    // PROVISIONAL ACCESS (D8) — PENDING grace window of 48h
    // =========================================================================

    /**
     * Sets the registered_at of a user to a specific age (relative to now) via JDBC,
     * so the 48h grace boundary can be exercised deterministically.
     */
    private void setRegisteredAgeHours(String email, long hoursAgo) {
        int updated = jdbcTemplate.update(
                "UPDATE users SET registered_at = NOW() - (? * INTERVAL '1 hour') WHERE email = ?",
                hoursAgo, email);
        org.assertj.core.api.Assertions.assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("R-2.4a: PENDING within 48h grace → 200 (provisional access)")
    void login_should_return_200_when_pending_within_grace() throws Exception {
        // Register creates a PENDING user — do NOT activate
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Fresh", "Pending", "fresh@example.com", "Password1"))))
                .andExpect(status().isCreated());
        setRegisteredAgeHours("fresh@example.com", 1); // 1h ago, well within grace

        mockMvc.perform(authPost("/api/auth/login", json(loginBody("fresh@example.com", "Password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("R-2.4b: PENDING past 48h grace → 403 ACCOUNT_NOT_ACTIVE")
    void login_should_return_403_when_pending_past_grace() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Old", "Pending", "oldpending@example.com", "Password1"))))
                .andExpect(status().isCreated());
        setRegisteredAgeHours("oldpending@example.com", 49); // past the 48h window

        mockMvc.perform(authPost("/api/auth/login", json(loginBody("oldpending@example.com", "Password1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("R-2.4c: INACTIVE → 403 ACCOUNT_NOT_ACTIVE regardless of age (no grace)")
    void login_should_return_403_when_inactive_even_if_recent() throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody("Inact", "User", "inactive@example.com", "Password1"))))
                .andExpect(status().isCreated());
        // Recently registered but deactivated → no grace
        jdbcTemplate.update("UPDATE users SET status = 'INACTIVE' WHERE email = ?", "inactive@example.com");
        setRegisteredAgeHours("inactive@example.com", 1);

        mockMvc.perform(authPost("/api/auth/login", json(loginBody("inactive@example.com", "Password1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_ACTIVE"));
    }
}
