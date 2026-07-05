package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
     * Check whether a user with the given id exists in the given lifecycle status. Used to validate
     * registered participants on reservation creation (a forged/inactive {@code userId} must be
     * rejected).
     */
    boolean existsByIdAndStatus(Long id, UserStatus status);

    /**
     * Check whether a user with the given login already exists.
     */
    boolean existsByLogin(String login);

    /**
     * Return a page of users filtered by status.
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);

    /**
     * Search ACTIVE users by first name, last name, full name or email (case-insensitive, partial
     * match). Only ACTIVE accounts are returned — PENDING/INACTIVE users are not selectable as
     * partners. The email participates in the WHERE clause only; it is never selected into the
     * response DTO (RN-RGPD). {@code pageable} caps the number of rows.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = com.padelpro.auth.domain.model.UserStatus.ACTIVE
              AND (
                    LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
                 OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
                 OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
              )
            ORDER BY u.firstName ASC, u.lastName ASC
            """)
    List<User> searchByNameOrEmail(@Param("term") String term, Pageable pageable);
}
