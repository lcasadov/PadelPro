package com.padelpro.auth.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Custom handler for {@code 403 Forbidden} responses.
 *
 * <p>Replaces Spring Security's default HTML error page with a machine-readable
 * JSON body ({@link ErrorResponse}) and records the denied access in the audit log.
 *
 * <p>Registered in {@link com.padelpro.auth.infrastructure.config.SecurityConfig}
 * via {@code .exceptionHandling(ex -> ex.accessDeniedHandler(this))}.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(AuditLogRepositoryPort auditLogRepositoryPort,
                                     ObjectMapper objectMapper) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {

        // Record in audit log
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String details = "uri=" + request.getRequestURI();
        if (auth != null && auth.getPrincipal() != null) {
            details += " userId=" + auth.getPrincipal();
        }

        auditLogRepositoryPort.save(new AuditLog(
                "ACCESS_DENIED",
                null,
                getClientIp(request),
                details,
                OffsetDateTime.now()
        ));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse("ACCESS_DENIED", "Insufficient permissions")
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
