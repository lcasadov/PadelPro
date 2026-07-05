package com.padelpro.auth.infrastructure.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiting filter using Bucket4j.
 *
 * <p>Thresholds (RN-AUTH-09):
 * <ul>
 *   <li>POST /api/auth/login — 5 requests / minute / IP</li>
 *   <li>POST /api/auth/register — 3 requests / minute / IP</li>
 *   <li>POST /api/auth/refresh — 5 requests / minute / IP (same public-auth budget as login)</li>
 *   <li>POST /api/pagos/webhook — 60 requests / minute / IP (public, unauthenticated Redsys
 *       notification; the ceiling is high enough to tolerate legitimate Redsys retries yet caps a
 *       flood of forged notifications that would otherwise hammer signature verification, D3).</li>
 * </ul>
 *
 * <p>When a bucket is exhausted the filter returns HTTP 429 Too Many Requests
 * with a {@code Retry-After} header (seconds until next refill) and a JSON body
 * {@code {"error":"RATE_LIMIT_EXCEEDED"}}.
 *
 * <p>Buckets are stored in a {@link ConcurrentHashMap} keyed by {@code <endpoint>:<ip>}.
 * This is an in-process store — it resets on restart and is not shared across instances.
 * A distributed store (Redis) would be needed for multi-instance deployments.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH    = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String REFRESH_PATH  = "/api/auth/refresh";
    private static final String WEBHOOK_PATH  = "/api/pagos/webhook";

    private static final int LOGIN_CAPACITY    = 5;
    private static final int REGISTER_CAPACITY = 3;
    private static final int REFRESH_CAPACITY  = 5;
    private static final int WEBHOOK_CAPACITY  = 60;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = resolvePath(request);
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Integer capacity = null;
        if (LOGIN_PATH.equals(path)) {
            capacity = LOGIN_CAPACITY;
        } else if (REGISTER_PATH.equals(path)) {
            capacity = REGISTER_CAPACITY;
        } else if (REFRESH_PATH.equals(path)) {
            capacity = REFRESH_CAPACITY;
        } else if (WEBHOOK_PATH.equals(path)) {
            capacity = WEBHOOK_CAPACITY;
        }

        if (capacity == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        String bucketKey = path + ":" + ip;
        int cap = capacity;

        Bucket bucket = buckets.computeIfAbsent(
                bucketKey,
                k -> buildBucket(cap)
        );

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = bucket.estimateAbilityToConsume(1)
                    .getNanosToWaitForRefill() / 1_000_000_000L + 1;

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            Map.of("error", "RATE_LIMIT_EXCEEDED")));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Bucket buildBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, REFILL_PERIOD)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves the application-relative request path in a way that is robust across both real
     * servlet containers and Spring MockMvc.
     *
     * <p>{@code getServletPath()} returns the empty string under MockMvc (the path lands in the
     * request URI instead), which silently disabled rate limiting in MockMvc-based tests. Using
     * {@code getRequestURI()} minus the context path works identically in both environments and
     * also survives a non-root deployment context path.
     */
    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
