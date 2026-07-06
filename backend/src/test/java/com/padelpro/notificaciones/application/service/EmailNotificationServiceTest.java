package com.padelpro.notificaciones.application.service;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
