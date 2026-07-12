package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.RefreshToken;

import java.util.Optional;

/**
 * Outbound port — persistence operations on {@link RefreshToken}.
 *
 * <p>Application services depend on this interface rather than the concrete
 * Spring Data repository, satisfying ArchUnit Rule 3 (Issue #79).
 */
public interface RefreshTokenRepositoryPort {

    /**
     * Persist a new refresh token entry.
     * Only the SHA-256 hash of the raw token is stored — never the raw value (RN-RGPD-04).
     *
     * @param refreshToken the token entry to save
     * @return the saved entry with its generated id
     */
    RefreshToken save(RefreshToken refreshToken);

    /**
     * Look up a refresh token by its SHA-256 hash (used by the refresh-token rotation flow).
     * The raw token is never stored — only the hash is persisted (RN-RGPD-04).
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw refresh token
     * @return the matching token entry, or empty if none exists
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically revoke a refresh token identified by its hash, but only if it is still active
     * (not yet revoked). Executed as a single conditional UPDATE so that two concurrent refresh
     * requests carrying the same token cannot both succeed (MEDIO-1: refresh-token rotation must
     * be atomic to preserve the replay/reuse mitigation).
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw refresh token
     * @return the number of rows updated: {@code 1} for the request that wins the race (the token
     *         was active and is now revoked), {@code 0} for any request that finds it already
     *         revoked (reuse / lost race). Callers MUST treat {@code 0} as an invalid token.
     */
    int revokeByTokenHashIfActive(String tokenHash);

    /**
     * Revoke ALL refresh tokens belonging to a user ({@code revoked = true}), regardless of their
     * current state. Used by the RGPD right-to-be-forgotten flow (capability exportaciones-rgpd,
     * RN-RGPD-07): when an account is anonymized every active session must be terminated so the
     * anonymized user cannot keep operating with a previously issued token.
     *
     * <p>Idempotent: re-running it on a user whose tokens are already revoked simply updates 0 rows.
     *
     * @param userId the id of the user whose tokens are revoked
     */
    void revokeAllByUserId(Long userId);
}
