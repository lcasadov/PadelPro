package com.padelpro.auth.infrastructure.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitFilter#resetBuckets()} (Issue #181 — test isolation).
 *
 * <p>Exercises the filter directly (no Spring context) so it runs in the unit test phase. Proves that
 * two identical bursts from the same IP do <b>not</b> share a counter once {@code resetBuckets()} is
 * called between them — the exact leak that made {@code RateLimitIntegrationTest} flaky when the
 * singleton bucket store carried state across test classes.
 */
@DisplayName("RateLimitFilter#resetBuckets — isolates counters between bursts")
class RateLimitFilterResetTest {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String IP = "203.0.113.7";
    private static final int LOGIN_CAPACITY = 5;

    private final RateLimitFilter filter = new RateLimitFilter(new ObjectMapper());

    private int performLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_PATH);
        request.setRequestURI(LOGIN_PATH);
        request.addHeader("X-Forwarded-For", IP);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("a fresh burst after resetBuckets() does not inherit the previous counter")
    void reset_gives_each_burst_a_full_fresh_bucket() throws Exception {
        // Arrange — exhaust the login bucket for this IP (5 allowed, 6th is 429).
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            assertThat(performLogin()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(performLogin()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // Act — reset all buckets (what integration tests do in @BeforeEach).
        filter.resetBuckets();

        // Assert — the same IP now starts a brand-new full bucket, so the first request passes.
        assertThat(performLogin()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("without a reset the counter persists across bursts (guards the leak the fix targets)")
    void without_reset_the_counter_persists() throws Exception {
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            performLogin();
        }

        // No reset — the 6th request is still rate-limited.
        assertThat(performLogin()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
