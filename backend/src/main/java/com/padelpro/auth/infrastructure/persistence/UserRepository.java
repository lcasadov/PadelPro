package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * <p>{@code findById(Long)} and {@code save(User)} are declared on both
 * {@link UserRepositoryPort} and {@link JpaRepository}; with type erasure (ID = Long,
 * {@code <S extends User> S save(S)} → {@code save(User)}) the signatures coincide,
 * so Spring Data supplies a single concrete proxy implementation for each. No bridging
 * {@code default} is needed — and a {@code default} that throws would shadow the proxy
 * at runtime (Spring Data does not back interface {@code default} methods), breaking
 * every caller (Issue #160).
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
     * Check if another user (different id) already uses this email.
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Check whether a user with the given login already exists.
     */
    boolean existsByLogin(String login);

    /**
     * Return a page of users filtered by status.
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);
}
