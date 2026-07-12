package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    /**
     * Find a user by their primary key.
     */
    Optional<User> findById(Long id);

    /**
     * Find a user by their linked Telegram chat id (auth-otp-telegram). Used by the webhook to
     * enforce that a Telegram chat is linked to at most one account (spec Requirement 1, scenario 3).
     */
    Optional<User> findByTelegramChatId(String telegramChatId);

    /**
     * Batch-load users by id (anti-N+1). Used to resolve participant display names for the
     * open-matches listing without one query per participant. The signature coincides with
     * {@code JpaRepository.findAllById} under type erasure, so the Spring Data proxy supplies it.
     *
     * @param ids the user ids to fetch
     * @return the matching users (order/size not guaranteed to match {@code ids})
     */
    List<User> findAllById(Iterable<Long> ids);

    /**
     * Check if another user (different id) already uses this email.
     * Used to detect email conflicts on profile updates.
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Check whether a user exists with the given id and status.
     *
     * <p>Used to validate that a registered participant attached to a reservation refers to a real
     * member in the expected lifecycle state (security: prevents a caller from attaching an arbitrary
     * or forged {@code userId}, or a non-{@code ACTIVE} account, to their reservation).
     *
     * @param id     the user id to check
     * @param status the required lifecycle status
     * @return {@code true} if a user with this id and status exists
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
     * Return a page of all users (no status filter).
     */
    Page<User> findAll(Pageable pageable);

    /**
     * Check whether at least one user with the given role exists.
     * Used by the admin-seed bootstrap (D1) to stay idempotent.
     */
    boolean existsByRole(com.padelpro.auth.domain.model.UserRole role);

    /**
     * Search ACTIVE users whose first name, last name, full name or email contains the given term
     * (case-insensitive, partial match). Used by the registered-partner selector (D5). The email is
     * only a filter criterion — callers must never project it into the response (RN-RGPD).
     *
     * @param term     the search term (already trimmed and length-validated by the caller)
     * @param pageable limits the result size to avoid dumping the directory
     * @return the matching users, ordered by name; empty if none match
     */
    List<User> searchByNameOrEmail(String term, Pageable pageable);
}
