package com.padelpro.auth.infrastructure.config.security;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CustomAccessDeniedHandler (GREEN phase after implementation).
 */
@ExtendWith(MockitoExtension.class)
class CustomAccessDeniedHandlerTest {

    @Mock
    private AuditLogRepositoryPort auditLogRepositoryPort;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private CustomAccessDeniedHandler handler;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        handler = new CustomAccessDeniedHandler(auditLogRepositoryPort, userRepositoryPort, objectMapper);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/api/admin/usuarios");
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should_write_json_error_response_on_403")
    void should_write_json_error_response_on_403() throws Exception {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");

        handler.handle(request, response, ex);

        verify(response).setStatus(403);
        verify(response).setContentType("application/json");
        String body = responseWriter.toString();
        assertThat(body).contains("ACCESS_DENIED");
    }

    @Test
    @DisplayName("should_log_access_denied_to_audit_log_with_user_fk")
    void should_log_access_denied_to_audit_log_with_user_fk() throws Exception {
        // Configure SecurityContext with valid auth (userId="42")
        OffsetDateTime now = OffsetDateTime.now();
        User user = new User("user42", "hash", "Test", "User",
                "user42@example.com", UserRole.USER, UserStatus.ACTIVE, now, now);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "42", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(user));

        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        handler.handle(request, response, ex);

        // Verify audit log saved with actual User entity (not null)
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepositoryPort).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();
        assertThat(saved.getAction()).isEqualTo("ACCESS_DENIED");
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getDetails()).contains("uri=/api/admin/usuarios");

        verify(userRepositoryPort).findById(eq(42L));
    }

    @Test
    @DisplayName("should_log_access_denied_with_null_user_when_no_auth")
    void should_log_access_denied_with_null_user_when_no_auth() throws Exception {
        // No SecurityContext — anonymous request
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        handler.handle(request, response, ex);

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepositoryPort).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getUser()).isNull();
    }
}
