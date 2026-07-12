package com.padelpro.notificaciones.application.service;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.model.NotificationType;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TDD unit tests for {@link EmailNotificationService} (task 2.1): PENDING→SENT on success,
 * PENDING→FAILED on exception (never propagated), and RN-RGPD-04 (no secrets in the recorded
 * message/recipient/error).
 *
 * <p>Uses hand-rolled fakes (not Mockito) so the {@link NotificationLog} state transitions are
 * observed on the very instances persisted by the service.
 */
@DisplayName("EmailNotificationService — record + deliver, fault-tolerant, no PII")
class EmailNotificationServiceTest {

    /** In-memory {@link NotificationLogPort} capturing every saved snapshot. */
    private static final class FakeLogPort implements NotificationLogPort {
        final List<NotificationLog> saved = new ArrayList<>();

        @Override
        public NotificationLog save(NotificationLog entry) {
            saved.add(entry);
            return entry;
        }

        @Override
        public Optional<NotificationLog> findById(UUID id) {
            return saved.stream().filter(n -> id.equals(n.getId())).findFirst();
        }

        @Override
        public List<NotificationLog> findRetriable(int maxAttempts) {
            return saved.stream()
                    .filter(n -> n.getStatus() == NotificationStatus.FAILED
                            && n.getAttempts() < maxAttempts)
                    .toList();
        }

        NotificationLog last() {
            return saved.get(saved.size() - 1);
        }
    }

    private FakeLogPort logPort;

    @BeforeEach
    void setUp() {
        logPort = new FakeLogPort();
    }

    @Test
    @DisplayName("2.1 successful delivery records the notification as SENT")
    void records_sent_on_success() {
        NotificationPort port = new NoopNotificationPort(); // succeeds
        EmailNotificationService service = new EmailNotificationService(logPort, port);

        service.dispatch(new EmailMessage("ana@example.com", "Reserva confirmada",
                "Tu reserva del 10/01/2030 está confirmada."), 7L, "RESERVATION", "res-1");

        NotificationLog result = logPort.last();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getRelatedEntityType()).isEqualTo("RESERVATION");
    }

    @Test
    @DisplayName("2.1 a delivery exception is recorded as FAILED and never propagates")
    void records_failed_on_exception_without_propagating() {
        NotificationPort port = new ThrowingNotificationPort();
        EmailNotificationService service = new EmailNotificationService(logPort, port);

        assertThatCode(() -> service.dispatch(
                new EmailMessage("ana@example.com", "Reserva confirmada", "cuerpo"),
                7L, "RESERVATION", "res-1"))
                .doesNotThrowAnyException();

        NotificationLog result = logPort.last();
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("2.1 RN-RGPD-04: no password/token/OTP/card ever reaches the recorded entry")
    void no_sensitive_data_recorded() {
        NotificationPort port = new ThrowingNotificationPort();
        EmailNotificationService service = new EmailNotificationService(logPort, port);

        // The service records exactly what the caller passes; the templates never include secrets.
        service.dispatch(new EmailMessage("ana@example.com", "Recibo de pago",
                "Importe: 15.00 €\nReferencia: 123456"), 7L, "PAYMENT", "pay-1");

        NotificationLog result = logPort.last();
        String haystack = (result.getMessage() + " " + result.getRecipient() + " "
                + result.getSubject() + " " + result.getErrorMessage()).toLowerCase();
        assertThat(haystack)
                .doesNotContain("password")
                .doesNotContain("contraseña")
                .doesNotContain("token")
                .doesNotContain("otp")
                .doesNotContain("cvv")
                .doesNotContain("tarjeta");
        // The stored error is a sanitized class name, not the raw body or a stack trace.
        assertThat(result.getErrorMessage()).doesNotContain("Importe");
    }

    // -------------------------------------------------------------------------
    // additional branches (change backend-branch-coverage)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("dispatch skips delivery when the PENDING entry cannot even be recorded")
    void dispatch_skips_when_pending_not_recorded() {
        final boolean[] attempted = {false};
        NotificationLogPort throwingPort = new NotificationLogPort() {
            @Override public NotificationLog save(NotificationLog e) {
                throw new RuntimeException("db down");
            }
            @Override public Optional<NotificationLog> findById(UUID id) { return Optional.empty(); }
            @Override public List<NotificationLog> findRetriable(int maxAttempts) { return List.of(); }
        };
        NotificationPort port = new NotificationPort() {
            @Override public void sendWelcomeEmail(WelcomeEmail email) { }
            @Override public void sendEmail(EmailMessage email) { attempted[0] = true; }
        };
        EmailNotificationService service = new EmailNotificationService(throwingPort, port);

        assertThatCode(() -> service.dispatch(
                new EmailMessage("ana@example.com", "s", "b"), 7L, "RESERVATION", "res-1"))
                .doesNotThrowAnyException();
        assertThat(attempted[0]).isFalse(); // delivery never attempted
    }

    @Test
    @DisplayName("attemptSend swallows a failure to persist the delivery outcome")
    void attempt_send_swallows_outcome_persistence_error() {
        NotificationLogPort port = new NotificationLogPort() {
            @Override public NotificationLog save(NotificationLog e) {
                if (e.getStatus() == NotificationStatus.PENDING) return e; // first save ok
                throw new RuntimeException("outcome save failed");          // outcome save fails
            }
            @Override public Optional<NotificationLog> findById(UUID id) { return Optional.empty(); }
            @Override public List<NotificationLog> findRetriable(int maxAttempts) { return List.of(); }
        };
        EmailNotificationService service = new EmailNotificationService(port, new NoopNotificationPort());

        assertThatCode(() -> service.dispatch(
                new EmailMessage("ana@example.com", "s", "b"), 7L, "RESERVATION", "res-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("retryFailed re-attempts a FAILED entry once its backoff window has elapsed")
    void retry_reattempts_when_backoff_elapsed() {
        FakeLogPort port = new FakeLogPort();
        NotificationLog failed = NotificationLog.pending(NotificationType.EMAIL, 7L,
                "ana@example.com", "s", "b", "RESERVATION", "res-1");
        failed.markFailed("err", OffsetDateTime.now().minusHours(1)); // FAILED, attempts=1, old attempt
        port.saved.add(failed);
        EmailNotificationService service = new EmailNotificationService(
                port, new NoopNotificationPort(), OffsetDateTime::now);

        int attempted = service.retryFailed();

        assertThat(attempted).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("retryFailed skips a FAILED entry still inside its backoff window")
    void retry_skips_within_backoff() {
        FakeLogPort port = new FakeLogPort();
        NotificationLog failed = NotificationLog.pending(NotificationType.EMAIL, 7L,
                "ana@example.com", "s", "b", "RESERVATION", "res-1");
        failed.markFailed("err", OffsetDateTime.now()); // attempts=1 → 2 min backoff, not elapsed
        port.saved.add(failed);
        EmailNotificationService service = new EmailNotificationService(
                port, new NoopNotificationPort(), OffsetDateTime::now);

        assertThat(service.retryFailed()).isZero();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("retryFailed re-attempts an entry that was never attempted (no lastAttemptAt)")
    void retry_reattempts_when_no_prior_attempt() {
        NotificationLogPort port = new NotificationLogPort() {
            @Override public NotificationLog save(NotificationLog e) { return e; }
            @Override public Optional<NotificationLog> findById(UUID id) { return Optional.empty(); }
            @Override public List<NotificationLog> findRetriable(int maxAttempts) {
                // lastAttemptAt is null → backoff considered elapsed
                return List.of(NotificationLog.pending(NotificationType.EMAIL, 7L,
                        "ana@example.com", "s", "b", "RESERVATION", "res-1"));
            }
        };
        EmailNotificationService service = new EmailNotificationService(port, new NoopNotificationPort());

        assertThat(service.retryFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("maskEmail covers all shapes (RN-RGPD-04)")
    void mask_email_branches() {
        assertThat(EmailNotificationService.maskEmail(null)).isEqualTo("<none>");
        assertThat(EmailNotificationService.maskEmail("  ")).isEqualTo("<none>");
        assertThat(EmailNotificationService.maskEmail("no-at-symbol")).isEqualTo("***");
        assertThat(EmailNotificationService.maskEmail("ab@example.com")).isEqualTo("a***@example.com");
        assertThat(EmailNotificationService.maskEmail("abcd@example.com")).isEqualTo("ab***@example.com");
    }

    private static final class NoopNotificationPort implements NotificationPort {
        @Override
        public void sendWelcomeEmail(com.padelpro.notificaciones.domain.model.WelcomeEmail email) {
        }

        @Override
        public void sendEmail(EmailMessage email) {
            // success — no-op
        }
    }

    private static final class ThrowingNotificationPort implements NotificationPort {
        @Override
        public void sendWelcomeEmail(com.padelpro.notificaciones.domain.model.WelcomeEmail email) {
        }

        @Override
        public void sendEmail(EmailMessage email) {
            throw new org.springframework.mail.MailSendException("SMTP down");
        }
    }
}
