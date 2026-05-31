package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.AuditLogEntryResponse;
import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.application.dto.PagedAuditLogResponse;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-TDD — Unit tests for AuditLogService.
 *
 * <p>TDD RED phase: written before AuditLogService implementation exists.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepositoryPort auditLogRepositoryPort;

    @InjectMocks
    private AuditLogService auditLogService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuditLog buildAuditLog(Long id, String action, User user) {
        AuditLog log = new AuditLog(action, user, "127.0.0.1", "some details",
                OffsetDateTime.now());
        // Reflection trick to set generated id in tests
        try {
            var field = AuditLog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(log, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return log;
    }

    private User buildUser(Long id, String email) {
        User user = new User("login", "hash", "First", "Last", email,
                UserRole.USER, UserStatus.ACTIVE,
                OffsetDateTime.now(), OffsetDateTime.now());
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // 1. No filter → returns paginated results
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_return_page_of_all_logs_when_no_filter")
    void should_return_page_of_all_logs_when_no_filter() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        AuditLog entry = buildAuditLog(1L, "LOGIN_SUCCESS", null);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(eq(filter), eq(pageable))).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        verify(auditLogRepositoryPort).findFiltered(eq(filter), eq(pageable));
    }

    // -------------------------------------------------------------------------
    // 2. Filter by action
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_filter_by_action")
    void should_filter_by_action() {
        AuditLogFilter filter = new AuditLogFilter("ACCESS_DENIED", null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        AuditLog entry = buildAuditLog(2L, "ACCESS_DENIED", null);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(eq(filter), eq(pageable))).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).action()).isEqualTo("ACCESS_DENIED");
    }

    // -------------------------------------------------------------------------
    // 3. Filter by userId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_filter_by_userId")
    void should_filter_by_userId() {
        AuditLogFilter filter = new AuditLogFilter(null, 42L, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        User user = buildUser(42L, "user42@example.com");
        AuditLog entry = buildAuditLog(3L, "LOGIN_SUCCESS", user);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(eq(filter), eq(pageable))).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).userId()).isEqualTo(42L);
    }

    // -------------------------------------------------------------------------
    // 4. Filter by date range
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_filter_by_date_range")
    void should_filter_by_date_range() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to   = OffsetDateTime.now();
        AuditLogFilter filter = new AuditLogFilter(null, null, null, from, to);
        Pageable pageable = PageRequest.of(0, 20);

        AuditLog entry = buildAuditLog(4L, "USER_REGISTERED", null);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(eq(filter), eq(pageable))).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        assertThat(response.content()).hasSize(1);
        verify(auditLogRepositoryPort).findFiltered(eq(filter), eq(pageable));
    }

    // -------------------------------------------------------------------------
    // 5. Validation: size > 100 throws ValidationException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_throw_when_size_exceeds_100")
    void should_throw_when_size_exceeds_100() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        Pageable bigPage = PageRequest.of(0, 500);

        assertThatThrownBy(() -> auditLogService.findAuditLogs(filter, bigPage))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("size must be between 1 and 100");
    }

    // -------------------------------------------------------------------------
    // 6. Mapping: user present → userId and userEmail populated
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_map_audit_log_to_entry_response_with_user_email")
    void should_map_audit_log_to_entry_response_with_user_email() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        User user = buildUser(7L, "test@example.com");
        AuditLog entry = buildAuditLog(10L, "LOGIN_SUCCESS", user);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(any(), any())).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        AuditLogEntryResponse dto = response.content().get(0);
        assertThat(dto.userId()).isEqualTo(7L);
        assertThat(dto.userEmail()).isEqualTo("test@example.com");
    }

    // -------------------------------------------------------------------------
    // 7. Mapping: user null → userId and userEmail are null
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_map_audit_log_to_entry_response_with_null_user")
    void should_map_audit_log_to_entry_response_with_null_user() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        AuditLog entry = buildAuditLog(11L, "LOGIN_FAILURE", null);
        Page<AuditLog> page = new PageImpl<>(List.of(entry), pageable, 1L);
        when(auditLogRepositoryPort.findFiltered(any(), any())).thenReturn(page);

        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);

        AuditLogEntryResponse dto = response.content().get(0);
        assertThat(dto.userId()).isNull();
        assertThat(dto.userEmail()).isNull();
    }
}
