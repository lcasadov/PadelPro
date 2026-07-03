package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
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
    // deactivateUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should_deactivate_user_and_audit")
    void should_deactivate_user_and_audit() {
        when(userRepositoryPort.findById(2L)).thenReturn(Optional.of(activeUser));
        when(userRepositoryPort.save(any(User.class))).thenReturn(activeUser);
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.deactivateUser(2L, 3L);

        assertThat(activeUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("should_throw_admin_self_deactivation_exception")
    void should_throw_admin_self_deactivation_exception() {
        assertThatThrownBy(() -> userAdminService.deactivateUser(3L, 3L))
                .isInstanceOf(AdminSelfDeactivationException.class);
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
