package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.RefreshToken;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link RefreshToken} entity.
 *
 * <p>Implements {@link RefreshTokenRepositoryPort} so application services depend on the
 * domain port rather than this concrete adapter (ArchUnit Rule 3, Issue #79).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepositoryPort {

    /*
     * NOTE: {@code save(RefreshToken)} is declared on both {@link RefreshTokenRepositoryPort}
     * and {@link JpaRepository}; with type erasure ({@code <S extends RefreshToken> S save(S)}
     * → {@code save(RefreshToken)}) the signatures coincide, so Spring Data supplies a single
     * concrete proxy implementation. No bridging {@code default} is needed — and a {@code default}
     * that throws would shadow the proxy at runtime (Spring Data does not back interface
     * {@code default} methods), breaking every caller, e.g. AuthService.login (Issue #161,
     * same antipattern as #160 in UserRepository).
     */

    /**
     * Look up a refresh token by its SHA-256 hash.
     * The raw token is never stored — only the hash is persisted (RN-RGPD-04).
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw refresh token
     * @return the matching token entry, or empty if not found or already revoked
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically revoke a single refresh token by its hash, only if it is still active.
     *
     * <p>The {@code AND r.revoked = false} predicate makes this a compare-and-set: the database
     * updates exactly one row for the first request that reaches it and zero rows for any later
     * request carrying the same (now-revoked) token. This is the concurrency gate that decides the
     * winner of a rotation race — two concurrent refreshes with the same token can no longer both
     * mint a new valid token (MEDIO-1).
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw refresh token
     * @return {@code 1} if the token was active and is now revoked, {@code 0} otherwise
     */
    @Override
    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.tokenHash = :tokenHash AND r.revoked = false")
    int revokeByTokenHashIfActive(@Param("tokenHash") String tokenHash);

    /**
     * Revoke all refresh tokens for a given user.
     * Triggered on password change, logout-all-devices, account deactivation, or RGPD anonymization
     * (capability exportaciones-rgpd, RN-RGPD-07).
     *
     * @param userId the ID of the user whose tokens should be revoked
     */
    @Override
    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);
}
