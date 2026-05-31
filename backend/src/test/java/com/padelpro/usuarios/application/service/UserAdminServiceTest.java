package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
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
    @DisplayName("should_create_user_with_active_status_and_bcrypt_hash")
    void should_create_user_with_active_status_and_bcrypt_hash() {
        when(userRepositoryPort.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepositoryPort.existsByLogin("newlogin")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword1")).thenReturn("$2a$12$hashed");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setId(u, 10L);
            return u;
        });
        when(auditLogRepositoryPort.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUserAdminCommand cmd = new CreateUserAdminCommand(
                "newlogin", "New", "User", "new@example.com", "P@ssword1", null, null);

        UserAdminResponse response = userAdminService.createUser(cmd);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.role()).isEqualTo("USER");
        verify(passwordEncoder).encode("P@ssword1");
        verify(auditLogRepositoryPort).save(any(AuditLog.class));
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
