package com.padelpro.auth.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
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
 * JSON body ({@link ErrorResponse}) and records the denied access in the audit log
 * with a proper {@code user_id} FK (not just a string in the details field).
 *
 * <p>Registered in {@link com.padelpro.auth.infrastructure.config.SecurityConfig}
 * via {@code .exceptionHandling(ex -> ex.accessDeniedHandler(this))}.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(AuditLogRepositoryPort auditLogRepositoryPort,
                                     UserRepositoryPort userRepositoryPort,
                                     ObjectMapper objectMapper) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {

        // Resolve the User entity from the JWT principal so the audit log
        // stores a proper user_id FK, not just a string in the details field.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = resolveUser(auth);
        String details = "uri=" + request.getRequestURI();

        auditLogRepositoryPort.save(new AuditLog(
                "ACCESS_DENIED",
                user,
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

    /**
     * Resolves the {@link User} from the security context principal.
     * Returns {@code null} if there is no principal or the principal is not a numeric user id.
     */
    private User resolveUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            long userId = Long.parseLong(auth.getPrincipal().toString());
            return userRepositoryPort.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
