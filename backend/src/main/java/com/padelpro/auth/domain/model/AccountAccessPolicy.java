package com.padelpro.auth.domain.model;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Domain policy deciding whether an account may access the app (D8 — provisional access).
 *
 * <ul>
 *   <li>{@code ACTIVE} → always allowed.</li>
 *   <li>{@code PENDING} → allowed only while within the grace window
 *       ({@code registeredAt + graceWindow >= now}); a freshly registered user thus gets
 *       provisional access for the configured window (48h by default).</li>
 *   <li>{@code INACTIVE} → always denied (deactivated by an admin; no grace).</li>
 * </ul>
 *
 * <p>Pure domain object: no Spring, no persistence. The grace window is injected so it can be
 * configured and exercised at the boundaries in isolation.
 */
public class AccountAccessPolicy {

    private final Duration graceWindow;

    public AccountAccessPolicy(Duration graceWindow) {
        this.graceWindow = graceWindow;
    }

    /**
     * @param status       the account status
     * @param registeredAt when the account was registered (basis of the grace window)
     * @param now          the reference instant
     * @return {@code true} if access is permitted
     */
    public boolean isAccessAllowed(UserStatus status, OffsetDateTime registeredAt, OffsetDateTime now) {
        if (status == UserStatus.ACTIVE) {
            return true;
        }
        if (status == UserStatus.PENDING) {
            OffsetDateTime deadline = registeredAt.plus(graceWindow);
            return !now.isAfter(deadline);
        }
        // INACTIVE (and any other non-active state) → no access, no grace.
        return false;
    }
}
