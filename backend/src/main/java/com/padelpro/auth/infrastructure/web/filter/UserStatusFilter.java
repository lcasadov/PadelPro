package com.padelpro.auth.infrastructure.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.auth.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Security filter that re-validates the user's account status on every request.
 *
 * <p>After {@link JwtAuthFilter} populates the {@link SecurityContextHolder}, this
 * filter checks that the authenticated user is still ACTIVE in the database.
 * If the user has been deactivated (INACTIVE or PENDING) after their JWT was issued,
 * the request is rejected with HTTP 403 even though the JWT signature is valid.
 *
 * <p>Registered at {@code @Order(2)} — always runs after {@link JwtAuthFilter}.
 * Requests without an authenticated principal (no JWT, or JWT already failed validation)
 * are passed through unchanged so that Spring Security's own 401 handling applies.
 */
@Component
@Order(2)
public class UserStatusFilter extends OncePerRequestFilter {

    private final UserRepositoryPort userRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final ObjectMapper objectMapper;

    public UserStatusFilter(UserRepositoryPort userRepositoryPort,
                            AuditLogRepositoryPort auditLogRepositoryPort,
                            ObjectMapper objectMapper) {
        this.userRepositoryPort = userRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Principal is userId as String (set by JwtAuthFilter)
        String principalStr = auth.getPrincipal().toString();
        long userId;
        try {
            userId = Long.parseLong(principalStr);
        } catch (NumberFormatException e) {
            // Not a user-id principal — pass through
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepositoryPort.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            SecurityContextHolder.clearContext();

            // Record the denied access in the audit log
            String statusDetail = "status=" + (user != null ? user.getStatus() : "null");
            auditLogRepositoryPort.save(new AuditLog(
                    "ACCESS_DENIED",
                    user,
                    getClientIp(request),
                    statusDetail,
                    OffsetDateTime.now()
            ));

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    new ErrorResponse("ACCOUNT_NOT_ACTIVE", "Account is not active")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
