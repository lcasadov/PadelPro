package com.padelpro.notificaciones.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for the {@link NotificationLog} audit record (change
 * backend-branch-coverage, D1/D2). Covers the {@code onCreate} defaults, the SENT/FAILED
 * transitions with error truncation, and the identity contract. Same package so the JPA-protected
 * {@code onCreate} is reachable.
 */
@DisplayName("Unit — NotificationLog (domain)")
class NotificationLogTest {

    private static final int MAX_ERROR_LEN = 500;

    private static NotificationLog pending() {
        return NotificationLog.pending(NotificationType.EMAIL, 7L, "ana@example.com",
                "Reserva confirmada", "cuerpo", "RESERVATION", "res-1");
    }

    private static void setId(NotificationLog n, UUID id) {
        try {
            var f = NotificationLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(n, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("factory + onCreate")
    class Lifecycle {

        @Test
        @DisplayName("pending() records a PENDING entry with zero attempts")
        void factory_pending() {
            NotificationLog n = pending();
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(n.getAttempts()).isZero();
            assertThat(n.getUserId()).isEqualTo(7L);
            assertThat(n.getRelatedEntityType()).isEqualTo("RESERVATION");
            assertThat(n.getRelatedEntityId()).isEqualTo("res-1");
        }

        @Test
        @DisplayName("onCreate assigns id/createdAt/status only when unset (true branches)")
        void onCreate_sets_defaults() {
            NotificationLog n = pending();
            n.onCreate();
            assertThat(n.getId()).isNotNull();
            assertThat(n.getCreatedAt()).isNotNull();
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("onCreate does not overwrite id/createdAt when already set (false branches)")
        void onCreate_keeps_existing() {
            NotificationLog n = pending();
            n.onCreate();
            UUID id = n.getId();
            var createdAt = n.getCreatedAt();
            n.onCreate();
            assertThat(n.getId()).isEqualTo(id);
            assertThat(n.getCreatedAt()).isEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("markSent / markFailed")
    class Transitions {

        @Test
        @DisplayName("markSent → SENT, stamps sent_at, clears error, counts the attempt")
        void mark_sent() {
            NotificationLog n = pending();
            OffsetDateTime at = OffsetDateTime.now();
            n.markSent(at);
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.getSentAt()).isEqualTo(at);
            assertThat(n.getLastAttemptAt()).isEqualTo(at);
            assertThat(n.getErrorMessage()).isNull();
            assertThat(n.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("markFailed with a short error stores it verbatim (truncate false branch)")
        void mark_failed_short() {
            NotificationLog n = pending();
            n.markFailed("MailSendException", OffsetDateTime.now());
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getErrorMessage()).isEqualTo("MailSendException");
            assertThat(n.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("markFailed with a null error keeps it null (truncate null branch)")
        void mark_failed_null() {
            NotificationLog n = pending();
            n.markFailed(null, OffsetDateTime.now());
            assertThat(n.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("markFailed with an over-long error truncates to the column cap (truncate true branch)")
        void mark_failed_truncated() {
            NotificationLog n = pending();
            String longError = "x".repeat(MAX_ERROR_LEN + 50);
            n.markFailed(longError, OffsetDateTime.now());
            assertThat(n.getErrorMessage()).hasSize(MAX_ERROR_LEN);
        }
    }

    @Nested
    @DisplayName("equals / hashCode identity contract")
    class Identity {

        @Test
        @DisplayName("reflexive")
        void reflexive() {
            NotificationLog n = pending();
            setId(n, UUID.randomUUID());
            assertThat(n.equals(n)).isTrue();
        }

        @Test
        @DisplayName("transient (null id) never equal; hashCode falls back to identity")
        void transient_not_equal() {
            NotificationLog a = pending();
            NotificationLog b = pending();
            setId(b, UUID.randomUUID());
            assertThat(a.equals(b)).isFalse();
            assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
        }

        @Test
        @DisplayName("not equal to null nor to a different type")
        void not_equal_null_or_other_type() {
            NotificationLog n = pending();
            setId(n, UUID.randomUUID());
            assertThat(n.equals(null)).isFalse();
            assertThat(n.equals("x")).isFalse();
        }

        @Test
        @DisplayName("same id equal + same hashCode; different id not equal")
        void by_id() {
            UUID id = UUID.randomUUID();
            NotificationLog a = pending();
            NotificationLog b = pending();
            NotificationLog c = pending();
            setId(a, id);
            setId(b, id);
            setId(c, UUID.randomUUID());
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }
}
