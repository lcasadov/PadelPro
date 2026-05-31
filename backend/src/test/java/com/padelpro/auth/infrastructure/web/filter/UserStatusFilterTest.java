package com.padelpro.auth.infrastructure.web.filter;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserStatusFilter (GREEN phase after implementation).
 */
@ExtendWith(MockitoExtension.class)
class UserStatusFilterTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private AuditLogRepositoryPort auditLogRepositoryPort;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    // Real ObjectMapper — cannot be mocked because @InjectMocks only injects by type
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UserStatusFilter userStatusFilter;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        userStatusFilter = new UserStatusFilter(userRepositoryPort, auditLogRepositoryPort, objectMapper);
        responseWriter = new StringWriter();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User buildUser(Long id, UserStatus status) {
        User user = new User(
                "testuser",
                "hash",
                "Test",
                "User",
                "user@example.com",
                UserRole.USER,
                status,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        // Reflectively set the id since User.id is managed by JPA
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private void setAuthContext(String userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("should_return_403_when_user_is_inactive_despite_valid_jwt")
    void should_return_403_when_user_is_inactive_despite_valid_jwt() throws Exception {
        setAuthContext("42");
        User inactiveUser = buildUser(42L, UserStatus.INACTIVE);
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(inactiveUser));
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userStatusFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(any(), any());
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("should_return_403_when_user_is_pending_despite_valid_jwt")
    void should_return_403_when_user_is_pending_despite_valid_jwt() throws Exception {
        setAuthContext("42");
        User pendingUser = buildUser(42L, UserStatus.PENDING);
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(pendingUser));
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userStatusFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(any(), any());
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("should_pass_through_when_user_is_active")
    void should_pass_through_when_user_is_active() throws Exception {
        setAuthContext("42");
        User activeUser = buildUser(42L, UserStatus.ACTIVE);
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(activeUser));

        userStatusFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(403);
    }

    @Test
    @DisplayName("should_not_query_db_when_no_authentication_in_context")
    void should_not_query_db_when_no_authentication_in_context() throws Exception {
        // SecurityContext has no authentication

        userStatusFilter.doFilterInternal(request, response, filterChain);

        verify(userRepositoryPort, never()).findById(anyLong());
        verify(filterChain).doFilter(request, response);
    }
}
