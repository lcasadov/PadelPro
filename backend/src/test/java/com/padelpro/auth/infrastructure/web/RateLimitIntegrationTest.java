package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T-025 — Integration tests for rate limiting on auth endpoints.
 *
 * <p>TDD RED phase: tests should FAIL because the rate-limiting filter/interceptor
 * is not yet implemented (Wave 3). Requests will return 500 (stub) rather than
 * the expected 429.
 *
 * <p>Scenarios covered: R-4.2 (login rate limit) and R-4.3 (register rate limit).
 * Thresholds: 5 login attempts / min per IP, 3 register attempts / min per IP.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
class RateLimitIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("padelpro_test")
                    .withUsername("padelpro_test")
                    .withPassword("padelpro_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @Sql("/sql/cleanup.sql")
    void cleanUp() {
        // @Sql handles cleanup; method body intentionally empty
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String registerJson(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "firstName", "Test",
                "lastName", "User",
                "email", email,
                "password", "Password1"
        ));
    }

    private ResultActions performLogin(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, "WrongPassword1")));
    }

    private ResultActions performRegister(String emailSuffix) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("rate" + emailSuffix + "@example.com")));
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
