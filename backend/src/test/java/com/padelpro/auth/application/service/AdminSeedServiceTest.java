package com.padelpro.auth.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminSeedService} — bootstrap of the first ADMIN (D1, RN-AUTH-07).
 *
 * <p>Pure domain/application logic; the repository and encoder are mocked. The
 * {@code ApplicationRunner} wiring is tested separately by the integration test.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeedServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // cheap for tests

    // 2.1 — no ADMIN + email/password present → creates an ACTIVE ADMIN with a BCrypt hash
    @Test
    void should_create_active_admin_with_bcrypt_hash_when_no_admin_and_env_present() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        AdminSeedService service = new AdminSeedService(userRepository, encoder);
        boolean created = service.seedIfAbsent("boss@club.local", "S3cretPass!");

        assertThat(created).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEmail()).isEqualTo("boss@club.local");
        assertThat(saved.getLogin()).isEqualTo("boss@club.local");
        // password is hashed, never stored in clear
        assertThat(saved.getPasswordHash()).isNotEqualTo("S3cretPass!");
        assertThat(encoder.matches("S3cretPass!", saved.getPasswordHash())).isTrue();
    }

    // 2.3 — an ADMIN already exists → no second admin created (idempotent)
    @Test
    void should_not_create_admin_when_one_already_exists() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        AdminSeedService service = new AdminSeedService(userRepository, encoder);
        boolean created = service.seedIfAbsent("boss@club.local", "S3cretPass!");

        assertThat(created).isFalse();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // 2.4 — env vars missing/blank → no admin created, no failure (does not even query)
    @Test
    void should_not_create_admin_when_email_blank() {
        AdminSeedService service = new AdminSeedService(userRepository, encoder);

        assertThat(service.seedIfAbsent("", "S3cretPass!")).isFalse();
        assertThat(service.seedIfAbsent("  ", "S3cretPass!")).isFalse();
        assertThat(service.seedIfAbsent(null, "S3cretPass!")).isFalse();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_not_create_admin_when_password_blank() {
        AdminSeedService service = new AdminSeedService(userRepository, encoder);

        assertThat(service.seedIfAbsent("boss@club.local", "")).isFalse();
        assertThat(service.seedIfAbsent("boss@club.local", null)).isFalse();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
