package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Query derivation is used for simple lookups; complex queries will use
 * {@code @Query} JPQL in future iterations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their email address (used during login authentication).
     *
     * @param email the email address to look up
     * @return the matching user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by their login handle.
     *
     * @param login the login handle to look up
     * @return the matching user, or empty if not found
     */
    Optional<User> findByLogin(String login);

    /**
     * Check whether a user with the given email already exists.
     * Used during registration to detect duplicate emails before persisting.
     *
     * @param email the email address to check
     * @return {@code true} if a user with this email exists
     */
    boolean existsByEmail(String email);
}
