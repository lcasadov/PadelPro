package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.domain.model.User;
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

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-022 — Unit tests for AuthService.
 *
 * <p>TDD RED phase: all tests should FAIL with UnsupportedOperationException
 * until Wave 3 implements the actual business logic.
 *
 * <p>Scenarios covered: R-2.1 through R-2.4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildActiveUser(String email) {
        return new User(
                email,
                // BCrypt(12) hash of "Password1" — generated for Wave 3 Green phase
                "$2a$12$XeUGfFM7WvseSi9H8cLSdugcl7C38Y/UiiqH28Sjne6ix9xavuevi",
                "Test",
                "User",
                email,
                UserRole.USER,
                UserStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private User buildUserWithStatus(String email, UserStatus status) {
        return new User(
                email,
                // BCrypt(12) hash of "Password1" — generated for Wave 3 Green phase
                "$2a$12$XeUGfFM7WvseSi9H8cLSdugcl7C38Y/UiiqH28Sjne6ix9xavuevi",
                "Test",
                "User",
                email,
                UserRole.USER,
                status,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    // -------------------------------------------------------------------------
    // R-2.1 — Valid login returns access token + token type + expires_in
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.1: should return token pair when credentials are valid")
    void should_return_token_pair_when_credentials_are_valid() {
        // Arrange — lenient() because the stub won't be consumed while the service
        // throws UnsupportedOperationException in the RED phase.
        String email = "user@example.com";
        User activeUser = buildActiveUser(email);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(activeUser));

        LoginCommand command = new LoginCommand(email, "Password1");

        // Act
        TokenPair result = authService.login(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
    }

    // -------------------------------------------------------------------------
    // R-2.2 — Email not registered returns same error as wrong password (anti-enumeration)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.2: should throw auth exception when email is not found (anti-enumeration)")
    void should_throw_auth_exception_when_email_not_found() {
        // Arrange — lenient() for RED phase
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        LoginCommand command = new LoginCommand("unknown@example.com", "AnyPassword1");

        // Act & Assert
        assertThatThrownBy(() -> authService.login(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("AUTH_INVALID_CREDENTIALS")
                        .withFailMessage("Error for unknown email must be AUTH_INVALID_CREDENTIALS"));
    }

    // -------------------------------------------------------------------------
    // R-2.3 — Wrong password returns identical error to unknown email
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.3: should throw auth exception when password is wrong")
    void should_throw_auth_exception_when_password_is_wrong() {
        // Arrange — lenient() for RED phase
        String email = "user@example.com";
        User activeUser = buildActiveUser(email);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(activeUser));

        LoginCommand command = new LoginCommand(email, "WrongPassword1");

        // Act & Assert
        assertThatThrownBy(() -> authService.login(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("AUTH_INVALID_CREDENTIALS")
                        .withFailMessage("Error for wrong password must be AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("R-2.2 + R-2.3: error message is identical for unknown email and wrong password (anti-enumeration)")
    void should_return_same_error_for_unknown_email_and_wrong_password() {
        // Arrange — lenient() used because the stubs may not be consumed if the service
        // throws UnsupportedOperationException before reaching the repository call in
        // the RED phase. Once Wave 3 is implemented the stubs WILL be consumed and
        // lenient() becomes irrelevant (but harmless).
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail("known@example.com"))
                .thenReturn(Optional.of(buildActiveUser("known@example.com")));
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        LoginCommand wrongEmailCmd = new LoginCommand("unknown@example.com", "AnyPassword1");
        LoginCommand wrongPasswordCmd = new LoginCommand("known@example.com", "WrongPassword1");

        // Act & Assert — both must throw exception with the same message
        Exception exUnknownEmail = null;
        Exception exWrongPassword = null;

        try { authService.login(wrongEmailCmd); }
        catch (Exception e) { exUnknownEmail = e; }

        try { authService.login(wrongPasswordCmd); }
        catch (Exception e) { exWrongPassword = e; }

        assertThat(exUnknownEmail).isNotNull();
        assertThat(exWrongPassword).isNotNull();
        assertThat(exUnknownEmail.getClass()).isEqualTo(exWrongPassword.getClass());
        assertThat(exUnknownEmail.getMessage()).isEqualTo(exWrongPassword.getMessage());
    }

    // -------------------------------------------------------------------------
    // R-2.4 — PENDING account is rejected with ACCOUNT_NOT_ACTIVE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.4: should throw account not active when status is PENDING")
    void should_throw_account_not_active_when_status_is_pending() {
        // Arrange — lenient() for RED phase
        String email = "pending@example.com";
        User pendingUser = buildUserWithStatus(email, UserStatus.PENDING);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(pendingUser));

        LoginCommand command = new LoginCommand(email, "Password1");

        // Act & Assert
        assertThatThrownBy(() -> authService.login(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("ACCOUNT_NOT_ACTIVE")
                        .withFailMessage("PENDING account must throw ACCOUNT_NOT_ACTIVE"));
    }

    // -------------------------------------------------------------------------
    // R-2.4 — INACTIVE account is rejected with ACCOUNT_NOT_ACTIVE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.4: should throw account not active when status is INACTIVE")
    void should_throw_account_not_active_when_status_is_inactive() {
        // Arrange — lenient() for RED phase
        String email = "inactive@example.com";
        User inactiveUser = buildUserWithStatus(email, UserStatus.INACTIVE);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(inactiveUser));

        LoginCommand command = new LoginCommand(email, "Password1");

        // Act & Assert
        assertThatThrownBy(() -> authService.login(command))
                .isNotInstanceOf(UnsupportedOperationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("ACCOUNT_NOT_ACTIVE")
                        .withFailMessage("INACTIVE account must throw ACCOUNT_NOT_ACTIVE"));
    }

    // -------------------------------------------------------------------------
    // R-2.1 side-effect — last_login_at is updated on successful login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("R-2.1: should update last_login_at on successful login")
    void should_update_last_login_at_on_successful_login() {
        // Arrange — lenient() for RED phase
        String email = "user@example.com";
        User activeUser = buildActiveUser(email);
        org.mockito.Mockito.lenient()
                .when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(activeUser));
        org.mockito.Mockito.lenient()
                .when(userRepository.save(activeUser))
                .thenReturn(activeUser);

        LoginCommand command = new LoginCommand(email, "Password1");

        // Act
        authService.login(command);

        // Assert — the user's lastLoginAt must be set and the record saved
        assertThat(activeUser.getLastLoginAt())
                .isNotNull()
                .isAfterOrEqualTo(OffsetDateTime.now().minusSeconds(5));
        verify(userRepository).save(activeUser);
    }
}
