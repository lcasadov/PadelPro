package com.padelpro.notificaciones.application.service;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationType;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Orchestrates transactional email delivery + audit for the {@code notificaciones} capability (change
 * {@code notificaciones-eventos-email}, D2/D3).
 *
 * <p>The flow for every notification (task 2.2):
 * <ol>
 *   <li>record a {@link NotificationLog} entry as {@code PENDING} (RN-NOT-03);</li>
 *   <li>send through {@link NotificationPort#sendEmail};</li>
 *   <li>update the entry to {@code SENT}, or {@code FAILED} + a sanitized error on any exception.</li>
 * </ol>
 *
 * <p><b>Fault tolerance (RN-NOT-01/02):</b> {@link #dispatch} runs {@code @Async} and never propagates
 * an exception, so a slow or broken SMTP server never blocks nor reverts the reservation/payment
 * transaction that triggered it. Failed entries are picked up later by {@link #retryFailed} (Req 3).
 *
 * <p><b>RN-RGPD-04:</b> the {@code error_message} stored on failure is a class-name summary of the
 * exception, never the email body nor any secret.
 */
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    /** Delivery attempt cap (Req 3): after 3 failed attempts the entry stays {@code FAILED}. */
    public static final int MAX_ATTEMPTS = 3;

    private final NotificationLogPort notificationLogPort;
    private final NotificationPort notificationPort;
    private final Supplier<OffsetDateTime> clock;

    public EmailNotificationService(NotificationLogPort notificationLogPort,
                                    NotificationPort notificationPort) {
        this(notificationLogPort, notificationPort, OffsetDateTime::now);
    }

    /** Test constructor allowing a fixed clock for deterministic backoff assertions. */
    EmailNotificationService(NotificationLogPort notificationLogPort,
                             NotificationPort notificationPort,
                             Supplier<OffsetDateTime> clock) {
        this.notificationLogPort = notificationLogPort;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    /**
     * Record and deliver an email notification asynchronously. Never throws — a delivery failure is
     * captured as a {@code FAILED} log entry (RN-NOT-01/02).
     *
     * @param message           the email to send (no sensitive data — RN-RGPD-04)
     * @param userId            the recipient user id, or {@code null} if not resolvable
     * @param relatedEntityType source aggregate type (e.g. {@code RESERVATION}, {@code PAYMENT})
     * @param relatedEntityId   source aggregate id, as text
     */
    @Async("notificationsTaskExecutor")
    public void dispatch(EmailMessage message, Long userId,
                         String relatedEntityType, String relatedEntityId) {
        NotificationLog entry;
        try {
            entry = notificationLogPort.save(NotificationLog.pending(
                    NotificationType.EMAIL, userId, message.recipient(),
                    message.subject(), message.body(), relatedEntityType, relatedEntityId));
        } catch (Exception persistenceError) {
            // If we cannot even record the PENDING entry, do not blow up the async thread.
            log.warn("Could not record notification for {} — delivery skipped: {}",
                    message.recipient(), persistenceError.getClass().getSimpleName());
            return;
        }
        attemptSend(entry);
    }

    /**
     * Attempt delivery of a recorded entry and persist the outcome. Reused by {@link #dispatch} (first
     * attempt) and by {@link #retryFailed} (subsequent attempts). Never throws.
     */
    void attemptSend(NotificationLog entry) {
        try {
            notificationPort.sendEmail(new EmailMessage(
                    entry.getRecipient(), entry.getSubject(), entry.getMessage()));
            entry.markSent(clock.get());
            log.info("Notification {} delivered to {} (attempt {})",
                    entry.getId(), entry.getRecipient(), entry.getAttempts());
        } catch (Exception ex) {
            entry.markFailed(ex.getClass().getSimpleName(), clock.get());
            log.warn("Notification {} to {} failed (attempt {}): {}",
                    entry.getId(), entry.getRecipient(), entry.getAttempts(),
                    ex.getClass().getSimpleName());
        }
        try {
            notificationLogPort.save(entry);
        } catch (Exception persistenceError) {
            log.warn("Could not persist notification outcome for {}: {}",
                    entry.getId(), persistenceError.getClass().getSimpleName());
        }
    }

    /**
     * Retry the {@code FAILED} notifications still below {@link #MAX_ATTEMPTS}, honouring an
     * exponential backoff (Req 3, D3): an entry is only re-attempted once
     * {@code last_attempt_at + 2^attempts minutes} has elapsed. Never throws.
     *
     * @return the number of entries re-attempted in this cycle
     */
    public int retryFailed() {
        List<NotificationLog> retriable = notificationLogPort.findRetriable(MAX_ATTEMPTS);
        OffsetDateTime now = clock.get();
        int attempted = 0;
        for (NotificationLog entry : retriable) {
            if (backoffElapsed(entry, now)) {
                attemptSend(entry);
                attempted++;
            }
        }
        return attempted;
    }

    /**
     * Whether the backoff window for an entry has elapsed: no prior attempt timestamp, or
     * {@code last_attempt_at + 2^attempts minutes <= now}.
     */
    private boolean backoffElapsed(NotificationLog entry, OffsetDateTime now) {
        OffsetDateTime last = entry.getLastAttemptAt();
        if (last == null) {
            return true;
        }
        long backoffMinutes = 1L << Math.min(entry.getAttempts(), 16); // 2^attempts, capped
        return !now.isBefore(last.plusMinutes(backoffMinutes));
    }
}
