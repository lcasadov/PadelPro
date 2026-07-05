package com.padelpro.auth.infrastructure.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Redsys webhook rate limit (MEDIO-1, pagos-redsys-online, D3).
 *
 * <p>{@code POST /api/pagos/webhook} is public (no JWT); the only DoS/replay throttle is this filter.
 * Budget: 60 requests / minute / IP — tolerant of legitimate Redsys retries, hard cap on a flood.
 */
@DisplayName("RateLimitFilter — webhook Redsys (MEDIO-1)")
class RateLimitFilterWebhookTest {

    private static final String WEBHOOK_PATH = "/api/pagos/webhook";
    private static final int CAPACITY = 60;

    private final RateLimitFilter filter = new RateLimitFilter(new ObjectMapper());

    private MockHttpServletRequest webhookRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", WEBHOOK_PATH);
        req.setRequestURI(WEBHOOK_PATH);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("dentro del límite (≤60/min por IP) → pasa la cadena, sin 429")
    void within_limit_passes() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(webhookRequest(ip), res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
            assertThat(chain.getRequest()).isNotNull(); // chain actually invoked
        }
    }

    @Test
    @DisplayName("por encima del límite (petición nº 61 misma IP) → 429 RATE_LIMIT_EXCEEDED")
    void above_limit_returns_429() throws Exception {
        String ip = "203.0.113.20";
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilter(webhookRequest(ip), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(webhookRequest(ip), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(chain.getRequest()).isNull(); // blocked: chain NOT invoked
    }

    @Test
    @DisplayName("el límite es por IP: una IP distinta conserva su propio bucket")
    void limit_is_per_ip() throws Exception {
        String flooder = "203.0.113.30";
        for (int i = 0; i < CAPACITY + 5; i++) {
            filter.doFilter(webhookRequest(flooder), new MockHttpServletResponse(), new MockFilterChain());
        }

        // A fresh IP still gets through.
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(webhookRequest("203.0.113.31"), res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        assertThat(chain.getRequest()).isNotNull();
    }
}
