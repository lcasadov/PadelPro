package com.padelpro.notificaciones.infrastructure.persistence;

import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.model.NotificationType;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD integration tests for the {@code notification_log} persistence port (task 1.1) on a real
 * PostgreSQL — the V14 migration + JPA mapping are exercised end to end.
 */
@DisplayName("NotificationLogAdapter — persist PENDING→SENT/FAILED and query retriable (Postgres)")
class NotificationLogAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private NotificationLogPort port;

    @Test
    @DisplayName("1.1 saves a PENDING entry and transitions it to SENT")
    void saves_pending_then_sent() {
        NotificationLog entry = NotificationLog.pending(
                NotificationType.EMAIL, null, "ana@example.com",
                "Reserva confirmada", "Tu reserva del 2030-01-10 está confirmada.",
                "RESERVATION", UUID.randomUUID().toString());

        NotificationLog saved = port.save(entry);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();

        OffsetDateTime now = OffsetDateTime.now();
        saved.markSent(now);
        NotificationLog persisted = port.save(saved);

        NotificationLog reloaded = port.findById(persisted.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getSentAt()).isNotNull();
        assertThat(reloaded.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("1.1 saves a FAILED entry with an error message and increments attempts")
    void saves_failed_with_error() {
        NotificationLog entry = NotificationLog.pending(
                NotificationType.EMAIL, null, "ana@example.com",
                "Reserva confirmada", "cuerpo", "RESERVATION", UUID.randomUUID().toString());
        entry.markFailed("MailSendException", OffsetDateTime.now());

        NotificationLog reloaded = port.findById(port.save(entry).getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getErrorMessage()).isEqualTo("MailSendException");
        assertThat(reloaded.getSentAt()).isNull();
    }

    @Test
    @DisplayName("1.1 findRetriable returns FAILED entries below the attempt cap, excludes SENT and exhausted")
    void find_retriable_filters_by_status_and_attempts() {
        // FAILED with 1 attempt → retriable
        NotificationLog retriable = NotificationLog.pending(
                NotificationType.EMAIL, null, "r@example.com", "s", "m", "RESERVATION", "r1");
        retriable.markFailed("boom", OffsetDateTime.now());
        UUID retriableId = port.save(retriable).getId();

        // FAILED with 3 attempts → exhausted, NOT retriable
        NotificationLog exhausted = NotificationLog.pending(
                NotificationType.EMAIL, null, "e@example.com", "s", "m", "RESERVATION", "e1");
        exhausted.markFailed("boom", OffsetDateTime.now());
        exhausted.markFailed("boom", OffsetDateTime.now());
        exhausted.markFailed("boom", OffsetDateTime.now());
        UUID exhaustedId = port.save(exhausted).getId();

        // SENT → NOT retriable
        NotificationLog sent = NotificationLog.pending(
                NotificationType.EMAIL, null, "s@example.com", "s", "m", "RESERVATION", "s1");
        sent.markSent(OffsetDateTime.now());
        UUID sentId = port.save(sent).getId();

        List<UUID> ids = port.findRetriable(3).stream().map(NotificationLog::getId).toList();

        assertThat(ids).contains(retriableId);
        assertThat(ids).doesNotContain(exhaustedId, sentId);
    }
}
