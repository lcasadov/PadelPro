package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.domain.exception.RefreshTokenInvalidException;
import com.padelpro.auth.domain.model.RefreshToken;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService#refresh(String)} — refresh-token rotation (auth-session-refresh 1.2).
 *
 * <p>TDD RED phase: these tests fail with {@code UnsupportedOperationException} until the Green
 * implementation lands (1.3). Scenarios mirror {@code specs/auth-local/spec.md}: valid rotation,
 * absent/expired/revoked → {@link RefreshTokenInvalidException}, and reuse of a revoked token.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AuthServiceRefreshTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    /** AuthService uses constructor injection for the mandatory port; wire the optional one here. */
    @BeforeEach
    void injectOptionalPorts() {
        authService.setRefreshTokenRepository(refreshTokenRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User activeUser() {
        return new User(
                "user@example.com",
                "$2a$12$XeUGfFM7WvseSi9H8cLSdugcl7C38Y/UiiqH28Sjne6ix9xavuevi",
                "Test", "User", "user@example.com",
                UserRole.USER, UserStatus.ACTIVE,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Valid token → new access token + rotation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("valid refresh token → new access token, revokes used, persists a new one")
    void should_rotate_and_issue_new_tokens_when_valid() throws Exception {
        String rawToken = "valid-raw-refresh-token";
        User user = activeUser();
        RefreshToken stored = new RefreshToken(
                user, sha256Hex(rawToken),
                OffsetDateTime.now().plusSeconds(604800), null, OffsetDateTime.now());

        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(stored));

        TokenPair result = authService.refresh(rawToken);

        // New access token issued
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);

        // A brand-new raw refresh token is returned (rotation), different from the one used
        assertThat(result.rawRefreshToken())
                .as("rotation must issue a new raw refresh token")
                .isNotBlank()
                .isNotEqualTo(rawToken);

        // The used token is revoked and both the revoked and the new token are persisted
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        List<RefreshToken> saved = captor.getAllValues();

        assertThat(stored.isRevoked())
                .as("the used refresh token must be revoked")
                .isTrue();
        assertThat(saved)
                .as("both the revoked token and a fresh token are persisted")
                .anySatisfy(t -> assertThat(t.isRevoked()).isTrue())
                .anySatisfy(t -> {
                    assertThat(t.isRevoked()).isFalse();
                    assertThat(t.getTokenHash()).isNotEqualTo(sha256Hex(rawToken));
                    assertThat(t.getExpiresAt()).isAfter(OffsetDateTime.now().plusSeconds(604000));
                });
    }

    // -------------------------------------------------------------------------
    // Absent token → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null/blank refresh token → RefreshTokenInvalidException, no lookup or persistence")
    void should_throw_when_token_absent() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(RefreshTokenInvalidException.class)
                .hasMessageContaining("AUTH_REFRESH_INVALID");

        assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Unknown token → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unknown refresh token (not found) → RefreshTokenInvalidException")
    void should_throw_when_token_not_found() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("ghost-token"))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Expired token → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("expired refresh token → RefreshTokenInvalidException, not rotated")
    void should_throw_when_token_expired() throws Exception {
        String rawToken = "expired-raw-token";
        RefreshToken expired = new RefreshToken(
                activeUser(), sha256Hex(rawToken),
                OffsetDateTime.now().minusSeconds(1), null, OffsetDateTime.now().minusDays(8));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Revoked token / reuse → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("already-revoked refresh token (reuse) → RefreshTokenInvalidException, not rotated")
    void should_throw_when_token_already_revoked() throws Exception {
        String rawToken = "revoked-raw-token";
        RefreshToken revoked = new RefreshToken(
                activeUser(), sha256Hex(rawToken),
                OffsetDateTime.now().plusSeconds(604800), null, OffsetDateTime.now());
        revoked.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
