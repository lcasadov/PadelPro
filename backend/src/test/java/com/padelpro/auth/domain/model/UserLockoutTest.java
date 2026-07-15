package com.padelpro.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the account-lockout domain behaviour on {@link User} (H-1 / OWASP A07).
 *
 * <p>Pure domain logic — no Spring, no persistence. Verifies the state machine: counting failures,
 * tripping the lock at the threshold, the lock window, no-op while already locked, and reset.
 */
@DisplayName("User — account lockout (H-1)")
class UserLockoutTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private User newUser() {
        OffsetDateTime t = OffsetDateTime.now();
        return new User("user@example.com", "hash", "Test", "User", "user@example.com",
                UserRole.USER, UserStatus.ACTIVE, t, t);
    }

    @Test
    @DisplayName("a fresh user is not locked and has zero failed attempts")
    void fresh_user_is_not_locked() {
        User user = newUser();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("failures below the threshold accumulate without locking")
    void failures_below_threshold_do_not_lock() {
        User user = newUser();
        OffsetDateTime now = OffsetDateTime.now();

        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now);
        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.isLocked(now)).isFalse();
    }

    @Test
    @DisplayName("reaching the threshold locks the account for the configured window and resets the counter")
    void reaching_threshold_locks_for_window() {
        User user = newUser();
        OffsetDateTime now = OffsetDateTime.now();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now);
        }

        assertThat(user.isLocked(now)).isTrue();
        // Counter resets so a fresh set of attempts is available once the lock expires.
        assertThat(user.getFailedLoginAttempts()).isZero();
        // Still locked just before the window ends, free just after.
        assertThat(user.isLocked(now.plusMinutes(14))).isTrue();
        assertThat(user.isLocked(now.plusMinutes(16))).isFalse();
    }

    @Test
    @DisplayName("registering a failure while already locked does not extend the lock")
    void failure_while_locked_is_noop() {
        User user = newUser();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now);
        }
        OffsetDateTime lockedUntil = user.getLockedUntil();

        // A further attempt 5 minutes later must NOT push the lock further out.
        user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now.plusMinutes(5));

        assertThat(user.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("clearFailedLogins resets both the counter and the lock")
    void clear_resets_state() {
        User user = newUser();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            user.registerFailedLogin(MAX_ATTEMPTS, LOCKOUT, now);
        }
        assertThat(user.isLocked(now)).isTrue();

        user.clearFailedLogins();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isLocked(now)).isFalse();
    }
}
