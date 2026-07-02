package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.exception.AuthenticationException;
import com.padelpro.auth.domain.exception.InvalidPasswordException;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeMyPasswordService} — the user's own forced/normal password change
 * (D9). Verifies policy enforcement, BCrypt persistence and the clearing of must_change_password.
 */
@ExtendWith(MockitoExtension.class)
class ChangeMyPasswordServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private AuditLogRepositoryPort auditLogRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private ChangeMyPasswordService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new ChangeMyPasswordService(userRepository, auditLogRepository, encoder);
        user = new User(
                "u@example.com", encoder.encode("OldPass1"), "U", "Ser", "u@example.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        user.setMustChangePassword(true);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void should_change_password_and_clear_flag_when_valid() {
        service.changePassword(7L, "OldPass1", "NewPass1");

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("NewPass1", user.getPasswordHash())).isTrue();
        verify(userRepository).save(user);
        verify(auditLogRepository).save(any());
    }

    @Test
    void should_reject_when_current_password_wrong() {
        assertThatThrownBy(() -> service.changePassword(7L, "WrongOld1", "NewPass1"))
                .isInstanceOf(AuthenticationException.class);

        assertThat(user.isMustChangePassword()).isTrue(); // unchanged
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_reject_when_new_password_violates_policy() {
        assertThatThrownBy(() -> service.changePassword(7L, "OldPass1", "weak"))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, never()).save(any());
    }
}
