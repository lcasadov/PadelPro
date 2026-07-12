package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import com.padelpro.reservas.domain.port.out.ParticipantCommandPort;
import com.padelpro.usuarios.application.dto.CreateUserAdminCommand;
import com.padelpro.usuarios.application.dto.PagedUsersResponse;
import com.padelpro.usuarios.application.dto.UserAdminResponse;
import com.padelpro.usuarios.domain.exception.AdminSelfDeactivationException;
import com.padelpro.usuarios.domain.exception.EmailConflictException;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import com.padelpro.usuarios.domain.exception.UserNotPendingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminService — unit tests")
class UserAdminServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private AuditLogRepositoryPort auditLogRepositoryPort;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepositoryPort;

    @Mock
    private OtpCodeRepositoryPort otpCodeRepositoryPort;

    @Mock
    private ParticipantCommandPort participantCommandPort;

    @InjectMocks
    private UserAdminService userAdminService;

    private User pendingUser;
    private User activeUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        pendingUser = makeUser(1L, "pending.user", UserStatus.PENDING, UserRole.USER);
        activeUser  = makeUser(2L, "active.user",  UserStatus.ACTIVE,  UserRole.USER);
        adminUser   = makeUser(3L, "admin.user",   UserStatus.ACTIVE,  UserRole.ADMIN);
    }

    // -------------------------------------------------------------------------
    // createUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_create_user_active_with_system_generated_password_and_must_change_flag (D2/D3)")
    void should_create_user_with_active_status_and_bcrypt_hash() {
        when(userRepositoryPort.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepositoryPort.existsByLogin("newlogin")).thenReturn(false);
        // D3: the system GENERATES the temporary password; the request password is ignored.
        when(temporaryPasswordGenerator.generate()).thenReturn("Gener4tedX9");
        when(passwordEncoder.encode("Gener4tedX9")).thenReturn("$2a$12$hashed");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setId(u, 10L);
            return u;
        });
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUserAdminCommand cmd = new CreateUserAdminCommand(
                "newlogin", "New", "User", "new@example.com", "IgnoredReqPw1", null, null);

        UserAdminResponse response = userAdminService.createUser(cmd);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.role()).isEqualTo("USER");
        // system-generated password is hashed; request password is NOT used
        verify(temporaryPasswordGenerator).generate();
        verify(passwordEncoder).encode("Gener4tedX9");
        verify(passwordEncoder, never()).encode("IgnoredReqPw1");
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("should_persist_must_change_password_true_and_send_welcome_with_generated_password (D3)")
    void should_send_welcome_email_with_generated_password_on_create() {
        when(userRepositoryPort.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepositoryPort.existsByLogin("newlogin")).thenReturn(false);
        when(temporaryPasswordGenerator.generate()).thenReturn("Gener4tedX9");
        when(passwordEncoder.encode("Gener4tedX9")).thenReturn("$2a$12$hashed");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepositoryPort.save(savedUser.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setId(u, 10L);
            return u;
        });
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUserAdminCommand cmd = new CreateUserAdminCommand(
                "newlogin", "New", "User", "new@example.com", null, null, null);

        userAdminService.createUser(cmd);

        // must_change_password = true on the persisted user
        assertThat(savedUser.getValue().isMustChangePassword()).isTrue();

        // welcome email carries the generated temporary password (D3 direct-creation flow)
        ArgumentCaptor<WelcomeEmail> emailCaptor = ArgumentCaptor.forClass(WelcomeEmail.class);
        verify(notificationPort).sendWelcomeEmail(emailCaptor.capture());
        WelcomeEmail email = emailCaptor.getValue();
        assertThat(email.recipientEmail()).isEqualTo("new@example.com");
        assertThat(email.hasPassword()).isTrue();
        assertThat(email.temporaryPassword()).isEqualTo("Gener4tedX9");
    }

    @Test
    @DisplayName("should_throw_email_conflict_when_creating_duplicate_email")
    void should_throw_email_conflict_when_creating_duplicate_email() {
        when(userRepositoryPort.existsByEmail("dup@example.com")).thenReturn(true);

        CreateUserAdminCommand cmd = new CreateUserAdminCommand(
                "somelogin", "A", "B", "dup@example.com", "P@ssword1", null, null);

        assertThatThrownBy(() -> userAdminService.createUser(cmd))
                .isInstanceOf(EmailConflictException.class);
    }

    // -------------------------------------------------------------------------
    // approveUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_approve_pending_user_and_audit")
    void should_approve_pending_user_and_audit() {
        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepositoryPort.save(any(User.class))).thenReturn(pendingUser);
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAdminResponse response = userAdminService.approveUser(1L);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("should_send_welcome_email_without_password_and_not_reset_on_approve (D3)")
    void should_send_welcome_email_without_password_on_approve() {
        String originalHash = pendingUser.getPasswordHash();
        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.approveUser(1L);

        // approval MUST NOT reset the password: the user keeps the one chosen at registration
        assertThat(pendingUser.getPasswordHash()).isEqualTo(originalHash);
        assertThat(pendingUser.isMustChangePassword()).isFalse();
        verify(temporaryPasswordGenerator, never()).generate();

        // welcome email WITHOUT password (approval flow)
        ArgumentCaptor<WelcomeEmail> emailCaptor = ArgumentCaptor.forClass(WelcomeEmail.class);
        verify(notificationPort).sendWelcomeEmail(emailCaptor.capture());
        WelcomeEmail email = emailCaptor.getValue();
        assertThat(email.recipientEmail()).isEqualTo(pendingUser.getEmail());
        assertThat(email.hasPassword()).isFalse();
        assertThat(email.temporaryPassword()).isNull();
    }

    @Test
    @DisplayName("should_complete_approve_even_when_email_send_fails (D4)")
    void should_complete_approve_even_when_email_fails() {
        when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(pendingUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP down"))
                .when(notificationPort).sendWelcomeEmail(any(WelcomeEmail.class));

        UserAdminResponse response = userAdminService.approveUser(1L);

        // activation completes despite the email failure (the port is best-effort/async)
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should_throw_when_approving_non_pending_user")
    void should_throw_when_approving_non_pending_user() {
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userAdminService.approveUser(2L))
                .isInstanceOf(UserNotPendingException.class);
    }

    // -------------------------------------------------------------------------
    // listUsers
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_return_paged_users_with_max_size_100")
    void should_return_paged_users_with_max_size_100() {
        // Request with size=200 — service must cap it at 100
        Pageable requested = PageRequest.of(0, 200);
        Page<User> page = new PageImpl<>(List.of(activeUser), PageRequest.of(0, 100), 1);
        when(userRepositoryPort.findAll(any(Pageable.class))).thenReturn(page);

        PagedUsersResponse response = userAdminService.listUsers(null, requested);

        assertThat(response.size()).isLessThanOrEqualTo(100);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // getUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_throw_not_found_when_user_does_not_exist")
    void should_throw_not_found_when_user_does_not_exist() {
        when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.getUser(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("999");
    }

    // -------------------------------------------------------------------------
    // deactivateUser → RGPD anonymization (capability exportaciones-rgpd)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_anonymize_user_revoke_tokens_invalidate_otp_anonymize_participants_and_audit (RN-RGPD-01/07)")
    void should_anonymize_user_and_related_data() {
        activeUser.setPhone("+34600111222");
        activeUser.setTelegramChatId("987654321");
        activeUser.setTelegramLinkedAt(OffsetDateTime.now());
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(activeUser));
        when(userRepositoryPort.findById(3L)).thenReturn(Optional.of(adminUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.deactivateUser(2L, 3L);

        // users anonymized (RN-RGPD-01/06)
        assertThat(activeUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(activeUser.getFirstName()).isEqualTo("ANONIMIZADO");
        assertThat(activeUser.getLastName()).isEqualTo("ANONIMIZADO");
        assertThat(activeUser.getEmail()).isEqualTo("anonimized-2@padelpro.local");
        assertThat(activeUser.getPhone()).isNull();
        assertThat(activeUser.getTelegramChatId()).isNull();
        assertThat(activeUser.getTelegramLinkedAt()).isNull();

        // sessions/otp terminated + participants anonymized, in the same flow (RN-RGPD-07/01)
        verify(refreshTokenRepositoryPort).revokeAllByUserId(2L);
        verify(otpCodeRepositoryPort).invalidateAllActiveByUserId(2L);
        verify(participantCommandPort).anonymizeByUserId(2L);

        // audit USER_ANONYMIZED, executor=admin (user_id=admin), entity USER/{targetId} (D3)
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepositoryPort).save(captor.capture());
        AuditLog audit = captor.getValue();
        assertThat(audit.getAction()).isEqualTo("USER_ANONYMIZED");
        assertThat(audit.getUser()).isSameAs(adminUser);
        assertThat(audit.getEntityType()).isEqualTo("USER");
        assertThat(audit.getEntityId()).isEqualTo("2");
        // RN-RGPD-04: personal data never leaks into the audit details
        String details = audit.getDetails() == null ? "" : audit.getDetails();
        assertThat(details).doesNotContain("active.user@example.com").doesNotContain("987654321");
    }

    @Test
    @DisplayName("should_reject_admin_anonymizing_own_account_and_touch_nothing (RN-AUTH-05)")
    void should_throw_admin_self_deactivation_exception() {
        assertThatThrownBy(() -> userAdminService.deactivateUser(3L, 3L))
                .isInstanceOf(AdminSelfDeactivationException.class);

        // guard fires before any mutation — no field/token/otp/participant/audit change
        verify(userRepositoryPort, never()).save(any());
        verify(refreshTokenRepositoryPort, never()).revokeAllByUserId(any());
        verify(otpCodeRepositoryPort, never()).invalidateAllActiveByUserId(any());
        verify(participantCommandPort, never()).anonymizeByUserId(any());
        verify(auditLogRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("should_not_modify_reservations_payments_or_audit_history — single new audit row (RN-RGPD-02/05)")
    void should_preserve_financial_and_audit_history() {
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(activeUser));
        when(userRepositoryPort.findById(3L)).thenReturn(Optional.of(adminUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.deactivateUser(2L, 3L);

        // exactly one NEW audit entry (history is immutable, never rewritten) — RN-RGPD-05
        verify(auditLogRepositoryPort, times(1)).save(any(AuditLog.class));
        // participants are anonymized via UPDATE, never inserted/deleted — RN-RGPD-02
        verify(participantCommandPort, times(1)).anonymizeByUserId(2L);
        verify(participantCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("should_be_idempotent_when_anonymizing_already_anonymized_user (D4)")
    void should_be_idempotent_on_double_anonymization() {
        User already = makeUser(2L, "active.user", UserStatus.INACTIVE, UserRole.USER);
        already.setFirstName("ANONIMIZADO");
        already.setLastName("ANONIMIZADO");
        already.setEmail("anonimized-2@padelpro.local");
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(already));
        when(userRepositoryPort.findById(3L)).thenReturn(Optional.of(adminUser));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // re-anonymizing must not fail; it re-writes the same neutral values (D4)
        userAdminService.deactivateUser(2L, 3L);

        assertThat(already.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(already.getEmail()).isEqualTo("anonimized-2@padelpro.local");
        assertThat(already.getFirstName()).isEqualTo("ANONIMIZADO");
        verify(refreshTokenRepositoryPort).revokeAllByUserId(2L);
        verify(otpCodeRepositoryPort).invalidateAllActiveByUserId(2L);
        verify(participantCommandPort).anonymizeByUserId(2L);
    }

    @Test
    @DisplayName("should_throw_not_found_when_anonymizing_missing_user")
    void should_throw_not_found_when_anonymizing_missing_user() {
        when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.deactivateUser(999L, 3L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("999");

        // nothing mutated when the target does not exist
        verify(refreshTokenRepositoryPort, never()).revokeAllByUserId(any());
        verify(otpCodeRepositoryPort, never()).invalidateAllActiveByUserId(any());
        verify(participantCommandPort, never()).anonymizeByUserId(any());
        verify(auditLogRepositoryPort, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // resetPassword (D3/D4/D9)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_reset_password_generate_temp_bcrypt_and_flag")
    void should_reset_password_generate_temp_bcrypt_and_flag() {
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(activeUser));
        when(temporaryPasswordGenerator.generate()).thenReturn("Temp0rary9");
        when(passwordEncoder.encode("Temp0rary9")).thenReturn("$2a$12$tempHash");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.resetPassword(2L, 3L);

        // temporary password returned in clear exactly once
        assertThat(result.temporaryPassword()).isEqualTo("Temp0rary9");
        // persisted as BCrypt, flag set
        assertThat(activeUser.getPasswordHash()).isEqualTo("$2a$12$tempHash");
        assertThat(activeUser.isMustChangePassword()).isTrue();
        verify(userRepositoryPort).save(activeUser);

        // audit must NOT contain the temporary password (RN-RGPD-04)
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepositoryPort).save(captor.capture());
        AuditLog audit = captor.getValue();
        String details = audit.getDetails() == null ? "" : audit.getDetails();
        assertThat(details).doesNotContain("Temp0rary9");
    }

    @Test
    @DisplayName("should_reject_admin_resetting_own_password (RN-AUTH-05)")
    void should_reject_admin_resetting_own_password() {
        assertThatThrownBy(() -> userAdminService.resetPassword(3L, 3L))
                .isInstanceOf(AdminSelfDeactivationException.class);
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    @DisplayName("should_throw_not_found_when_resetting_missing_user")
    void should_throw_not_found_when_resetting_missing_user() {
        when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.resetPassword(999L, 3L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private User makeUser(Long id, String login, UserStatus status, UserRole role) {
        User u = new User(login, "$2a$12$hash", "First", "Last",
                login + "@example.com", role, status,
                OffsetDateTime.now().minusDays(5), OffsetDateTime.now().minusDays(1));
        setId(u, id);
        return u;
    }

    private void setId(User u, Long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
