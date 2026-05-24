package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.RefreshToken;

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
}
