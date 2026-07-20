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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SeedUsersService} — real test-user provisioning (Issue #258).
 *
 * <p>Repository and encoder are mocked; the runner/flag wiring is only active when
 * {@code SEED_USERS_ENABLED=true} (set in docker-compose, not in the test context).
 */
@ExtendWith(MockitoExtension.class)
class SeedUsersServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // cheap for tests

    @Test
    void should_create_active_player_with_bcrypt_hash_when_email_absent() {
        when(userRepository.existsByEmail("jugador.demo@padelpro.es")).thenReturn(false);

        SeedUsersService service = new SeedUsersService(userRepository, encoder);
        boolean created = service.seedIfAbsent(
                "Jugador.Demo@PadelPro.es", "PadelDemo#Jugador2026", "Jugador", "Prueba", UserRole.USER);

        assertThat(created).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE); // ACTIVE, not PENDING → can log in
        assertThat(saved.getEmail()).isEqualTo("jugador.demo@padelpro.es"); // normalized lowercase
        assertThat(saved.getLogin()).isEqualTo("jugador.demo@padelpro.es");
        assertThat(saved.getPasswordHash()).isNotEqualTo("PadelDemo#Jugador2026");
        assertThat(encoder.matches("PadelDemo#Jugador2026", saved.getPasswordHash())).isTrue();
    }

    @Test
    void should_create_admin_role_when_requested() {
        when(userRepository.existsByEmail("admin.demo@padelpro.es")).thenReturn(false);

        SeedUsersService service = new SeedUsersService(userRepository, encoder);
        boolean created = service.seedIfAbsent(
                "admin.demo@padelpro.es", "PadelDemo#Admin2026", "Admin", "Prueba", UserRole.ADMIN);

        assertThat(created).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void should_be_idempotent_when_email_already_exists() {
        when(userRepository.existsByEmail("jugador.demo@padelpro.es")).thenReturn(true);

        SeedUsersService service = new SeedUsersService(userRepository, encoder);
        boolean created = service.seedIfAbsent(
                "jugador.demo@padelpro.es", "PadelDemo#Jugador2026", "Jugador", "Prueba", UserRole.USER);

        assertThat(created).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_not_create_when_credentials_blank() {
        SeedUsersService service = new SeedUsersService(userRepository, encoder);

        assertThat(service.seedIfAbsent("", "pw", "A", "B", UserRole.USER)).isFalse();
        assertThat(service.seedIfAbsent(null, "pw", "A", "B", UserRole.USER)).isFalse();
        assertThat(service.seedIfAbsent("x@y.z", "", "A", "B", UserRole.USER)).isFalse();
        assertThat(service.seedIfAbsent("x@y.z", null, "A", "B", UserRole.USER)).isFalse();
        verify(userRepository, never()).save(any());
    }
}
