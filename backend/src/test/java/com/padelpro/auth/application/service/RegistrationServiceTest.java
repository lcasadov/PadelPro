package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.UserDto;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-021 — Unit tests for RegistrationService.
 *
 * <p>TDD RED phase: all tests should FAIL with UnsupportedOperationException
 * until Wave 3 implements the actual business logic.
 *
 * <p>Scenarios covered: R-1.1 through R-1.4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RegistrationService registrationService;

    // -------------------------------------------------------------------------
    // R-1.1 — Valid registration creates a PENDING user
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.1: should create user with PENDING status when registration is valid")
    void should_create_user_with_pending_status_when_registration_is_valid() {
        // Arrange — lenient() because in the RED phase the service throws
        // UnsupportedOperationException before reaching the repository calls.
        // Once Wave 3 is implemented, these stubs WILL be consumed.
        org.mockito.Mockito.lenient()
                .when(userRepository.existsByEmail("alice@example.com"))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(userRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0,
                        com.padelpro.auth.domain.model.User.class));

        RegisterCommand command = new RegisterCommand(
                "Alice", "Smith", "alice@example.com", "Password1");

        // Act
        UserDto result = registrationService.register(command);

        // Assert basic DTO fields
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.role()).isEqualTo(UserRole.USER);

        // Assert R-1.1: the User passed to save() must have status=PENDING
        ArgumentCaptor<com.padelpro.auth.domain.model.User> userCaptor =
                ArgumentCaptor.forClass(com.padelpro.auth.domain.model.User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus())
                .as("newly registered user must be PENDING")
                .isEqualTo(UserStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // R-1.2 — Duplicate email is rejected with a domain exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.2: should throw exception when email already exists")
    void should_throw_exception_when_email_already_exists() {
        // Arrange — lenient() because the stub won't be consumed while the service
        // throws UnsupportedOperationException in the RED phase.
        org.mockito.Mockito.lenient()
                .when(userRepository.existsByEmail("existing@example.com"))
                .thenReturn(true);

        RegisterCommand command = new RegisterCommand(
                "Bob", "Jones", "existing@example.com", "Password1");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("EMAIL_ALREADY_REGISTERED")
                        .withFailMessage("Exception message must identify the EMAIL_ALREADY_REGISTERED error"));
    }

    // -------------------------------------------------------------------------
    // R-1.3a — Password too short
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.3a: should reject password shorter than 8 characters")
    void should_reject_password_shorter_than_8_chars() {
        // Arrange — no repository interaction expected for invalid password
        RegisterCommand command = new RegisterCommand(
                "Carol", "White", "carol@example.com", "Pass1");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("MIN_LENGTH_8")
                        .withFailMessage("Exception message must identify MIN_LENGTH_8 violation"));
    }

    // -------------------------------------------------------------------------
    // R-1.3b — Password without uppercase letter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.3b: should reject password without uppercase letter")
    void should_reject_password_without_uppercase() {
        // Arrange
        RegisterCommand command = new RegisterCommand(
                "Dave", "Brown", "dave@example.com", "password1");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("REQUIRES_UPPERCASE")
                        .withFailMessage("Exception message must identify REQUIRES_UPPERCASE violation"));
    }

    // -------------------------------------------------------------------------
    // R-1.3c — Password without digit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.3c: should reject password without number")
    void should_reject_password_without_number() {
        // Arrange
        RegisterCommand command = new RegisterCommand(
                "Eve", "Green", "eve@example.com", "PasswordNoDigit");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("REQUIRES_NUMBER")
                        .withFailMessage("Exception message must identify REQUIRES_NUMBER violation"));
    }

    // -------------------------------------------------------------------------
    // R-1.4 — Missing required field
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-1.4: should reject registration when first_name is missing")
    void should_reject_registration_when_required_field_is_missing() {
        // Arrange — null firstName simulates a missing required field
        RegisterCommand command = new RegisterCommand(
                null, "Smith", "frank@example.com", "Password1");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex)
                        .isNotNull()
                        .withFailMessage("A missing required field must throw a meaningful exception"));
    }

    @Test
    @DisplayName("R-1.4: should reject registration when email is missing")
    void should_reject_registration_when_email_is_missing() {
        // Arrange
        RegisterCommand command = new RegisterCommand(
                "Frank", "Smith", null, "Password1");

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex)
                        .isNotNull()
                        .withFailMessage("A missing email must throw a meaningful exception"));
    }

    @Test
    @DisplayName("R-1.4: should reject registration when password is missing")
    void should_reject_registration_when_password_is_missing() {
        // Arrange
        RegisterCommand command = new RegisterCommand(
                "Frank", "Smith", "frank@example.com", null);

        // Act & Assert
        assertThatThrownBy(() -> registrationService.register(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex)
                        .isNotNull()
                        .withFailMessage("A missing password must throw a meaningful exception"));
    }
}
