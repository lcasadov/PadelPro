package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.RefreshToken;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link RefreshToken} entity.
 *
 * <p>Implements {@link RefreshTokenRepositoryPort} so application services depend on the
 * domain port rather than this concrete adapter (ArchUnit Rule 3, Issue #79).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepositoryPort {

    /**
     * Resolves the ambiguity between {@link RefreshTokenRepositoryPort#save(RefreshToken)} and
     * the generic {@code <S>save(S)} from {@link JpaRepository}.
     */
    @Override
    default RefreshToken save(RefreshToken refreshToken) {
        throw new UnsupportedOperationException("Spring Data proxy must override this method");
    }

    /**
     * Look up a refresh token by its SHA-256 hash.
     * The raw token is never stored — only the hash is persisted (RN-RGPD-04).
     *
     * @param tokenHash the hex-encoded SHA-256 hash of the raw refresh token
     * @return the matching token entry, or empty if not found or already revoked
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke all active refresh tokens for a given user.
     * Triggered on password change, logout-all-devices, or account deactivation.
     *
     * @param userId the ID of the user whose tokens should be revoked
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAll(@Param("userId") Long userId);
}
