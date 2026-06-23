package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T-025 — Integration tests for rate limiting on auth endpoints.
 *
 * <p>Extends {@link PostgresIntegrationTest} so it runs both locally (Vía A external Postgres on
 * :5433) and in CI (Testcontainers), replacing the previous direct {@code @Testcontainers} setup.
 *
 * <p>The {@link com.padelpro.auth.infrastructure.web.filter.RateLimitFilter} keys its buckets by
 * client IP. Under MockMvc every request reports the same remote address, and the bucket store is a
 * singleton shared across the cached Spring context — so buckets would leak between test methods.
 * To keep each scenario independent, every method uses a distinct {@code X-Forwarded-For} IP (the
 * filter honours that header), giving it a fresh bucket regardless of execution order.
 *
 * <p>Scenarios covered: R-4.2 (login rate limit) and R-4.3 (register rate limit).
 * Thresholds: 5 login attempts / min per IP, 3 register attempts / min per IP.
 */
class RateLimitIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** A unique client IP per test method so each gets its own rate-limit bucket. */
    private String clientIp;

    @org.junit.jupiter.api.BeforeEach
    void assignUniqueClientIp() {
        clientIp = "10." + (int) (Math.random() * 254 + 1) + "."
                + (int) (Math.random() * 254 + 1) + "." + (int) (Math.random() * 254 + 1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String registerJson(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "first_name", "Test",
                "last_name", "User",
                "email", email,
                "password", "Password1"
        ));
    }

    private ResultActions performLogin(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "WrongPassword1")));
    }

    private ResultActions performRegister(String emailSuffix) throws Exception {
        // Unique email per run so a re-run against a non-cleaned DB never hits 409 before 429.
        return mockMvc.perform(post("/api/auth/register")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("rate-" + UUID.randomUUID() + "-" + emailSuffix + "@example.com")));
    }

    // =========================================================================
    // R-4.1 — Requests below the threshold are processed normally
    // =========================================================================

    @Test
    @DisplayName("R-4.1: login requests below threshold (≤5) are not rate-limited")
    void login_requests_below_threshold_are_not_rate_limited() throws Exception {
        // Send exactly 5 requests (the limit); none should be 429
        for (int i = 0; i < 5; i++) {
            performLogin("below_threshold@example.com")
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }
    }

    // =========================================================================
    // R-4.2 — Login rate limit: 5 requests per minute per IP
    // =========================================================================

    @Test
    @DisplayName("R-4.2: login rate limit should return 429 after 5 requests per minute")
    void login_rate_limit_should_return_429_after_5_requests_per_minute() throws Exception {
        // Arrange & Act — send 5 requests (all will be 401 — wrong credentials)
        for (int i = 0; i < 5; i++) {
            performLogin("ratetest@example.com");
        }

        // The 6th request must be rate-limited
        performLogin("ratetest@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("R-4.2: login 429 response must include Retry-After header")
    void login_rate_limit_should_include_retry_after_header_in_429_response() throws Exception {
        // Exhaust the login bucket
        for (int i = 0; i < 5; i++) {
            performLogin("retryafter@example.com");
        }

        // The 6th request must carry Retry-After
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("retryafter@example.com", "WrongPassword1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()));
    }

    // =========================================================================
    // R-4.3 — Register rate limit: 3 requests per minute per IP
    // =========================================================================

    @Test
    @DisplayName("R-4.3: register rate limit should return 429 after 3 requests per minute")
    void register_rate_limit_should_return_429_after_3_requests_per_minute() throws Exception {
        // Arrange & Act — send 3 register requests
        for (int i = 0; i < 3; i++) {
            performRegister(String.valueOf(i));
        }

        // The 4th request must be rate-limited
        performRegister("overflow")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().string("Retry-After", notNullValue()));
    }
}
