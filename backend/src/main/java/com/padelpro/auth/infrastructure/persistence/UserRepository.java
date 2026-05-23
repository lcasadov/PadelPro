package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Implements {@link UserRepositoryPort} so that application services depend on
 * the port interface (domain layer) rather than this concrete adapter, satisfying
 * the Dependency Inversion Principle enforced by ArchUnit Rule 3 (Issue #79).
 *
 * <p>The {@code default User save(User)} override resolves the compile-time
 * method-reference ambiguity between the port's concrete {@code save(User)} and
 * Spring Data's generic {@code <S extends User> S save(S)}, making the single
 * most-specific overload unambiguous for both Mockito stubs and production callers.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryPort {

    /**
     * Find a user by their email address (used during login authentication).
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by their login handle.
     */
    Optional<User> findByLogin(String login);

    /**
     * Check whether a user with the given email already exists.
     */
    boolean existsByEmail(String email);

    /**
     * Resolves the ambiguity between {@link UserRepositoryPort#save(User)} and the
     * generic {@code <S>save(S)} inherited from {@link JpaRepository}.
     * This explicit {@code default} override makes {@code save(User)} unambiguous
     * at every call site (production code, Mockito stubs, verify() calls).
     */
    @Override
    default User save(User user) {
        // In production this default is never called — Spring Data's proxy always
        // provides a concrete implementation that hits the database.
        // In Mockito mocks the proxy overrides this too; it is present solely to
        // eliminate the compile-time ambiguity.
        throw new UnsupportedOperationException(
                "Spring Data proxy must override this method");
    }
}
