package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.User;

import java.util.Optional;

/**
 * Outbound port — persistence operations on {@link User}.
 *
 * <p>Application services depend on this interface, never on the concrete
 * Spring Data repository. This enforces the Dependency Inversion Principle:
 * the infrastructure adapter ({@code UserRepository}) implements this port,
 * keeping the domain and application layers free of infrastructure coupling.
 *
 * <p>Introduced in Wave 3 to fix the ArchUnit violation tracked in Issue #79.
 */
public interface UserRepositoryPort {

    /**
     * Find a user by email address.
     *
     * @param email the email address to look up
     * @return the matching user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether a user with the given email already exists.
     *
     * @param email the email to check
     * @return {@code true} if a user with this email exists
     */
    boolean existsByEmail(String email);

    /**
     * Persist a user (insert or update).
     *
     * @param user the user entity to save
     * @return the persisted entity (may have {@code id} populated after insert)
     */
    User save(User user);
}
