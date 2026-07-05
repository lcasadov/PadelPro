package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.shared.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code POST /api/auth/refresh} (auth-session-refresh 1.4).
 *
 * <p>Covers the ADDED requirement "Renovación de access token vía refresh token":
 * valid cookie → 200 with a new access token + rotated {@code Set-Cookie}; absent cookie → 401;
 * invalid/expired/revoked cookie → 401; no {@code Authorization} header required.
 */
class RefreshTokenIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Unique client IP per test so the shared RateLimitFilter buckets do not leak across methods. */
    private String clientIp;

    @BeforeEach
    void assignUniqueClientIp() {
        clientIp = "10." + (int) (Math.random() * 254 + 1) + "."
                + (int) (Math.random() * 254 + 1) + "." + (int) (Math.random() * 254 + 1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private MockHttpServletRequestBuilder authPost(String path, String body) {
        return post(path)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Map<String, String> registerBody(String email, String password) {
        return Map.of("first_name", "Test", "last_name", "User", "email", email, "password", password);
    }

    private Map<String, String> loginBody(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    /** Register a user, activate it via JDBC, log in and return the refresh_token cookie. */
    private Cookie loginAndGetRefreshCookie(String email, String password) throws Exception {
        mockMvc.perform(authPost("/api/auth/register", json(registerBody(email, password))))
                .andExpect(status().isCreated());
        int updated = jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE email = ?", email);
        assertThat(updated).isEqualTo(1);

        MvcResult login = mockMvc.perform(authPost("/api/auth/login", json(loginBody(email, password))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("refresh_token");
        assertThat(cookie).as("login must set a refresh_token cookie").isNotNull();
        return cookie;
    }

    // -------------------------------------------------------------------------
    // Valid cookie → 200 + rotation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh with a valid cookie → 200 with new access_token and rotated Set-Cookie")
    void refresh_should_return_200_and_rotate_cookie_when_valid() throws Exception {
        Cookie refreshCookie = loginAndGetRefreshCookie("refresh-ok@example.com", "Password1");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        // The rotated cookie must carry a different value (a new raw refresh token).
        Cookie rotated = result.getResponse().getCookie("refresh_token");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue())
                .as("rotation must issue a new refresh token value")
                .isNotBlank()
                .isNotEqualTo(refreshCookie.getValue());

        // Cookie attributes mirror login (Path scoped to the refresh endpoint, 7-day Max-Age).
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/auth/refresh");
        assertThat(setCookie).contains("Max-Age=604800");
    }

    @Test
    @DisplayName("refresh does not require an Authorization header (access token is expired by design)")
    void refresh_should_not_require_authorization_header() throws Exception {
        Cookie refreshCookie = loginAndGetRefreshCookie("refresh-noauth@example.com", "Password1");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("the used refresh token cannot be reused → second refresh returns 401")
    void refresh_should_reject_reuse_of_rotated_token() throws Exception {
        Cookie refreshCookie = loginAndGetRefreshCookie("refresh-reuse@example.com", "Password1");

        // First use succeeds and revokes the original token.
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(refreshCookie))
                .andExpect(status().isOk());

        // Reusing the same (now-revoked) token → 401.
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_REFRESH_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Absent / invalid cookie → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh without a cookie → 401 AUTH_REFRESH_INVALID")
    void refresh_should_return_401_when_cookie_absent() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header("X-Forwarded-For", clientIp))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    @DisplayName("refresh with an unknown/invalid cookie value → 401 AUTH_REFRESH_INVALID")
    void refresh_should_return_401_when_cookie_invalid() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(new Cookie("refresh_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    @DisplayName("refresh with a revoked cookie → 401 AUTH_REFRESH_INVALID")
    void refresh_should_return_401_when_cookie_revoked() throws Exception {
        Cookie refreshCookie = loginAndGetRefreshCookie("refresh-revoked@example.com", "Password1");
        // Revoke every refresh token for this user out-of-band.
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked = true WHERE user_id = "
                        + "(SELECT id FROM users WHERE email = ?)", "refresh-revoked@example.com");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Forwarded-For", clientIp)
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTH_REFRESH_INVALID"));
    }
}
