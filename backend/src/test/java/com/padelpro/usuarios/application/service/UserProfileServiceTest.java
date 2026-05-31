package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.usuarios.application.dto.UpdateMyProfileCommand;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.domain.exception.EmailConflictException;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService — unit tests")
class UserProfileServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @InjectMocks
    private UserProfileService userProfileService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User(
                "juan.garcia",
                "$2a$12$hashedpassword",
                "Juan",
                "Garcia",
                "juan@example.com",
                UserRole.USER,
                UserStatus.ACTIVE,
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now().minusDays(1)
        );
        existingUser.setPhone("+34600000001");
        // inject id via reflection since there's no public setter
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(existingUser, 42L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("should_return_profile_when_user_exists")
    void should_return_profile_when_user_exists() {
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(existingUser));

        UserProfileResponse response = userProfileService.getMyProfile(42L);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.login()).isEqualTo("juan.garcia");
        assertThat(response.firstName()).isEqualTo("Juan");
        assertThat(response.email()).isEqualTo("juan@example.com");
        assertThat(response.phone()).isEqualTo("+34600000001");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.telegramLinked()).isFalse();
    }

    @Test
    @DisplayName("should_throw_user_not_found_when_user_missing")
    void should_throw_user_not_found_when_user_missing() {
        when(userRepositoryPort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMyProfile(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("should_update_profile_when_fields_are_valid")
    void should_update_profile_when_fields_are_valid() {
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByEmailAndIdNot("new@example.com", 42L)).thenReturn(false);
        when(userRepositoryPort.save(any(User.class))).thenReturn(existingUser);

        UpdateMyProfileCommand cmd = new UpdateMyProfileCommand(
                "NuevoNombre", "NuevoApellido", "new@example.com", "+34611111111");

        UserProfileResponse response = userProfileService.updateMyProfile(42L, cmd);

        assertThat(response).isNotNull();
        verify(userRepositoryPort).save(existingUser);
        assertThat(existingUser.getFirstName()).isEqualTo("NuevoNombre");
        assertThat(existingUser.getLastName()).isEqualTo("NuevoApellido");
        assertThat(existingUser.getEmail()).isEqualTo("new@example.com");
        assertThat(existingUser.getPhone()).isEqualTo("+34611111111");
    }

    @Test
    @DisplayName("should_throw_email_conflict_when_email_already_taken")
    void should_throw_email_conflict_when_email_already_taken() {
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(existingUser));
        when(userRepositoryPort.existsByEmailAndIdNot("taken@example.com", 42L)).thenReturn(true);

        UpdateMyProfileCommand cmd = new UpdateMyProfileCommand(
                null, null, "taken@example.com", null);

        assertThatThrownBy(() -> userProfileService.updateMyProfile(42L, cmd))
                .isInstanceOf(EmailConflictException.class);
    }
}
