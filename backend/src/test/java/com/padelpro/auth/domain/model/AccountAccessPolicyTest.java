package com.padelpro.auth.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AccountAccessPolicy} — the 48h provisional-access rule (D8),
 * tested in isolation from persistence and HTTP.
 *
 * <p>Rules:
 * <ul>
 *   <li>ACTIVE → always allowed.</li>
 *   <li>PENDING within the grace window (registeredAt + 48h) → allowed (provisional).</li>
 *   <li>PENDING past the grace window → denied.</li>
 *   <li>INACTIVE → always denied (no grace, regardless of age).</li>
 * </ul>
 */
class AccountAccessPolicyTest {

    private static final Duration GRACE = Duration.ofHours(48);
    private final AccountAccessPolicy policy = new AccountAccessPolicy(GRACE);

    private final OffsetDateTime now = OffsetDateTime.parse("2026-06-30T12:00:00Z");

    // 3.4 — ACTIVE → allowed regardless of age
    @Test
    void active_is_always_allowed() {
        assertThat(policy.isAccessAllowed(UserStatus.ACTIVE, now.minusYears(2), now)).isTrue();
        assertThat(policy.isAccessAllowed(UserStatus.ACTIVE, now, now)).isTrue();
    }

    // 3.1 — PENDING registered < 48h ago → allowed (provisional)
    @Test
    void pending_within_grace_is_allowed() {
        assertThat(policy.isAccessAllowed(UserStatus.PENDING, now.minusHours(1), now)).isTrue();
        assertThat(policy.isAccessAllowed(UserStatus.PENDING, now.minusHours(47), now)).isTrue();
    }

    // 3.1 boundary — exactly at the edge (registeredAt + 48h == now) is still allowed
    @Test
    void pending_at_exact_grace_boundary_is_allowed() {
        assertThat(policy.isAccessAllowed(UserStatus.PENDING, now.minusHours(48), now)).isTrue();
    }

    // 3.3 — PENDING registered > 48h ago → denied
    @Test
    void pending_past_grace_is_denied() {
        assertThat(policy.isAccessAllowed(UserStatus.PENDING, now.minusHours(48).minusSeconds(1), now)).isFalse();
        assertThat(policy.isAccessAllowed(UserStatus.PENDING, now.minusHours(72), now)).isFalse();
    }

    // 3.4 — INACTIVE → always denied, no grace even if just registered
    @Test
    void inactive_is_always_denied() {
        assertThat(policy.isAccessAllowed(UserStatus.INACTIVE, now, now)).isFalse();
        assertThat(policy.isAccessAllowed(UserStatus.INACTIVE, now.minusHours(1), now)).isFalse();
        assertThat(policy.isAccessAllowed(UserStatus.INACTIVE, now.minusYears(1), now)).isFalse();
    }
}
