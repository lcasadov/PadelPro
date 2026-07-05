package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.RefreshToken;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT — atomic refresh-token revocation contract (security finding MEDIO-1, auth-session-refresh).
 *
 * <p>Validates {@link RefreshTokenRepository#revokeByTokenHashIfActive(String)} against a real
 * PostgreSQL through the Spring Data proxy (Mockito would hide the DB-level compare-and-set). This
 * is the concurrency gate that decides the winner of a rotation race: the conditional
 * {@code UPDATE ... WHERE token_hash = :h AND revoked = false} must affect exactly one row the first
 * time and zero rows on any subsequent attempt with the same (now-revoked) token — so two
 * concurrent refreshes carrying the same token cannot both mint a new valid token (double-spend).
 *
 * <p>Extends {@link PostgresIntegrationTest}: connects to an external PostgreSQL via
 * {@code -Dit.postgres.url=...} (portable :5433 IT DB) or Testcontainers in CI.
 */
@DisplayName("IT — atomic refresh-token revocation (MEDIO-1, real Spring Data proxy)")
class RefreshTokenRevokeAtomicIT extends PostgresIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private User persistActiveUser() {
        OffsetDateTime now = OffsetDateTime.now();
        return userRepository.saveAndFlush(new User(
                "atomic.revoke", "$2a$12$0000000000000000000000000000000000000000000000000000",
                "Atomic", "Revoke", "atomic.revoke@example.com",
                UserRole.USER, UserStatus.ACTIVE, now, now));
    }

    @Test
    @DisplayName("first revokeByTokenHashIfActive returns 1, second returns 0 (reuse blocked)")
    void revoke_is_atomic_compare_and_set() {
        User user = persistActiveUser();
        OffsetDateTime now = OffsetDateTime.now();
        String tokenHash = "hash-atomic-0001";
        refreshTokenRepository.saveAndFlush(new RefreshToken(
                user, tokenHash, now.plusSeconds(604800), null, now));

        // First caller (winner of the race) revokes the active token → exactly one row updated.
        int first = refreshTokenRepository.revokeByTokenHashIfActive(tokenHash);
        assertThat(first)
                .as("the first conditional revoke must affect exactly one row")
                .isEqualTo(1);

        // Second caller (concurrent reuse / lost race) finds it already revoked → zero rows.
        int second = refreshTokenRepository.revokeByTokenHashIfActive(tokenHash);
        assertThat(second)
                .as("reuse of an already-revoked token must affect zero rows")
                .isZero();

        // The token is now persisted as revoked.
        assertThat(refreshTokenRepository.findByTokenHash(tokenHash))
                .get()
                .satisfies(t -> assertThat(t.isRevoked()).isTrue());
    }

    @Test
    @DisplayName("unknown token hash returns 0 (nothing to revoke)")
    void revoke_unknown_hash_returns_zero() {
        assertThat(refreshTokenRepository.revokeByTokenHashIfActive("hash-does-not-exist"))
                .isZero();
    }
}
