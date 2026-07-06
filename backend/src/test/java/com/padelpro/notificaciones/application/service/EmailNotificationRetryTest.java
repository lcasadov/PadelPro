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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for the retry logic of {@link EmailNotificationService#retryFailed()} (task 4.1),
 * which the {@code NotificationRetryJob} invokes on a schedule (task 4.2):
 * <ul>
 *   <li>picks up {@code FAILED} entries with {@code attempts < 3} and re-sends them;</li>
 *   <li>a successful re-send moves the entry to {@code SENT};</li>
 *   <li>after 3 failed attempts the entry stays {@code FAILED} and is never retried again;</li>
 *   <li>respects the exponential backoff ({@code last_attempt_at + 2^attempts min}).</li>
 * </ul>
 */
@DisplayName("EmailNotificationService.retryFailed — backoff + cap of 3 (Req 3)")
class EmailNotificationRetryTest {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2030, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    /** In-memory log port. */
    private static final class FakeLogPort implements NotificationLogPort {
        final List<NotificationLog> store = new ArrayList<>();

        @Override
        public NotificationLog save(NotificationLog entry) {
            if (!store.contains(entry)) {
                store.add(entry);
            }
            return entry;
        }

        @Override
        public Optional<NotificationLog> findById(UUID id) {
            return store.stream().filter(n -> id.equals(n.getId())).findFirst();
        }

        @Override
        public List<NotificationLog> findRetriable(int maxAttempts) {
            return store.stream()
                    .filter(n -> n.getStatus() == NotificationStatus.FAILED
                            && n.getAttempts() < maxAttempts)
                    .toList();
        }
    }

    /** Notification port whose send outcome is toggleable. */
    private static final class ToggleablePort implements NotificationPort {
        boolean fail = true;
        int sends = 0;

        @Override
        public void sendWelcomeEmail(WelcomeEmail email) {
        }

        @Override
        public void sendEmail(EmailMessage email) {
            sends++;
            if (fail) {
                throw new org.springframework.mail.MailSendException("SMTP down");
            }
        }
    }

    private FakeLogPort logPort;
    private ToggleablePort port;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        logPort = new FakeLogPort();
        port = new ToggleablePort();
        now = BASE;
    }

    private EmailNotificationService serviceWithClock() {
        return new EmailNotificationService(logPort, port, () -> now);
    }

    /** Seed a FAILED entry with the given attempt count whose last attempt was at BASE. */
    private NotificationLog seedFailed(int attempts) {
        NotificationLog entry = NotificationLog.pending(NotificationType.EMAIL, 1L,
                "ana@example.com", "Reserva confirmada", "cuerpo", "RESERVATION", "res-1");
        for (int i = 0; i < attempts; i++) {
            entry.markFailed("MailSendException", BASE);
        }
        return logPort.save(entry);
    }

    @Test
    @DisplayName("4.1 a FAILED entry past its backoff window is retried and succeeds → SENT")
    void retries_after_backoff_and_succeeds() {
        NotificationLog entry = seedFailed(1); // attempts=1 → backoff 2^1 = 2 min
        port.fail = false;                     // SMTP recovered
        now = BASE.plusMinutes(3);             // beyond the 2-min window

        int attempted = serviceWithClock().retryFailed();

        assertThat(attempted).isEqualTo(1);
        assertThat(port.sends).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entry.getAttempts()).isEqualTo(2);
        assertThat(entry.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("4.1 within the backoff window the entry is NOT retried")
    void does_not_retry_within_backoff_window() {
        NotificationLog entry = seedFailed(1); // backoff 2 min
        port.fail = false;
        now = BASE.plusMinutes(1);             // still inside the window

        int attempted = serviceWithClock().retryFailed();

        assertThat(attempted).isZero();
        assertThat(port.sends).isZero();
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("4.1 a retry that fails again stays FAILED with an incremented attempt count")
    void retry_that_fails_again_stays_failed() {
        NotificationLog entry = seedFailed(1);
        port.fail = true;                      // SMTP still down
        now = BASE.plusMinutes(3);

        int attempted = serviceWithClock().retryFailed();

        assertThat(attempted).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("4.1 after 3 failed attempts the entry is never retried again (cap)")
    void exhausted_entry_is_not_retried() {
        NotificationLog entry = seedFailed(3); // attempts=3 → at the cap
        port.fail = false;
        now = BASE.plusHours(1);               // well past any backoff

        int attempted = serviceWithClock().retryFailed();

        assertThat(attempted).isZero();
        assertThat(port.sends).isZero();
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getAttempts()).isEqualTo(3);
    }
}
