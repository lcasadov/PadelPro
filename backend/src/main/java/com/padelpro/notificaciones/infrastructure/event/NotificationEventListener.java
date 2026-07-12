package com.padelpro.notificaciones.infrastructure.event;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.application.service.TelegramNotificationService;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationCancelledEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.infrastructure.email.PaymentReceiptEmailTemplate;
import com.padelpro.notificaciones.infrastructure.email.ReservationCancelledEmailTemplate;
import com.padelpro.notificaciones.infrastructure.email.ReservationConfirmedEmailTemplate;
import com.padelpro.notificaciones.infrastructure.telegram.PaymentReceiptTelegramTemplate;
import com.padelpro.notificaciones.infrastructure.telegram.ReservationCancelledTelegramTemplate;
import com.padelpro.notificaciones.infrastructure.telegram.ReservationConfirmedTelegramTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Bridges reservation/payment domain events to transactional emails (change
 * {@code notificaciones-eventos-email}, D1).
 *
 * <p>Each handler runs {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so the email is only
 * scheduled once the business transaction (reservation confirm/cancel, payment PAID) has committed —
 * a rolled-back transaction publishes no email (RN-NOT-01). The actual send is offloaded to
 * {@link EmailNotificationService#dispatch} which is {@code @Async} and fault-tolerant, so a slow or
 * broken SMTP never blocks the committing thread (RN-NOT-02).
 *
 * <p><b>Fully off the request/webhook thread (#199):</b> every handler is additionally
 * {@code @Async("notificationsTaskExecutor")}, so the owner lookup ({@link UserRepositoryPort#findById})
 * and all template building run on the notifications pool rather than the thread that committed the
 * reservation/payment. This removes the extra owner-resolution round-trip from the latency of each
 * mutation. The {@code @Async} + {@code AFTER_COMMIT} combination is safe here: {@code AFTER_COMMIT}
 * already fires post-commit, so processing the event on another thread (outside the original
 * transaction) is correct — the business data is already durable and nothing is rolled back. The
 * owner is re-read inside its own repository transaction, and the handlers read no
 * {@code SecurityContext}/MDC (all needed data travels inside the event payload), so no thread-bound
 * context needs propagating. Rejection under load is handled by the executor's
 * {@code CallerRunsPolicy} (see {@code AsyncConfig}) — a saturated queue degrades to inline execution
 * rather than throwing back into the committing thread.
 *
 * <p>The recipient (owner/titular) email is resolved here from the user id. If the user cannot be
 * resolved or has no email (e.g. anonymized account), the notification is skipped — no email is sent
 * to a non-existent address (aligns with the security requirement of not mailing anonymized users).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private static final String ENTITY_RESERVATION = "RESERVATION";
    private static final String ENTITY_PAYMENT = "PAYMENT";

    private final EmailNotificationService emailNotificationService;
    private final TelegramNotificationService telegramNotificationService;
    private final UserRepositoryPort userRepositoryPort;

    public NotificationEventListener(EmailNotificationService emailNotificationService,
                                     TelegramNotificationService telegramNotificationService,
                                     UserRepositoryPort userRepositoryPort) {
        this.emailNotificationService = emailNotificationService;
        this.telegramNotificationService = telegramNotificationService;
        this.userRepositoryPort = userRepositoryPort;
    }

    @Async("notificationsTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationConfirmed(ReservationConfirmedEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            String reservationId = event.reservationId().toString();
            EmailMessage message = ReservationConfirmedEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.date(), event.startTime(),
                    event.durationMinutes(), event.amount());
            emailNotificationService.dispatch(message, owner.getId(), ENTITY_RESERVATION, reservationId);

            // RN-TEL-03: direct Telegram notification only to a linked recipient.
            if (hasTelegram(owner)) {
                telegramNotificationService.dispatchDirect(owner.getTelegramChatId(),
                        ReservationConfirmedTelegramTemplate.direct(owner.getFirstName(), event.date(),
                                event.startTime(), event.durationMinutes(), event.amount()),
                        owner.getId(), ENTITY_RESERVATION, reservationId);
            }
            // Req 6: broadcast to the club group when configured (no personal data). The service
            // no-ops when telegram_group_id is unset.
            telegramNotificationService.dispatchGroup(
                    ReservationConfirmedTelegramTemplate.group(event.date(), event.startTime(),
                            event.durationMinutes()),
                    ENTITY_RESERVATION, reservationId);
        });
    }

    @Async("notificationsTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelledEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            String reservationId = event.reservationId().toString();
            EmailMessage message = ReservationCancelledEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.reason());
            emailNotificationService.dispatch(message, owner.getId(), ENTITY_RESERVATION, reservationId);

            if (hasTelegram(owner)) {
                telegramNotificationService.dispatchDirect(owner.getTelegramChatId(),
                        ReservationCancelledTelegramTemplate.direct(owner.getFirstName(), event.reason()),
                        owner.getId(), ENTITY_RESERVATION, reservationId);
            }
        });
    }

    @Async("notificationsTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentPaid(PaymentPaidEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            String reservationId = event.reservationId().toString();
            EmailMessage message = PaymentReceiptEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.amount(),
                    event.paidAt(), event.reference());
            emailNotificationService.dispatch(message, owner.getId(), ENTITY_PAYMENT, reservationId);

            if (hasTelegram(owner)) {
                telegramNotificationService.dispatchDirect(owner.getTelegramChatId(),
                        PaymentReceiptTelegramTemplate.direct(owner.getFirstName(), event.amount(),
                                event.paidAt(), event.reference()),
                        owner.getId(), ENTITY_PAYMENT, reservationId);
            }
        });
    }

    /** Whether the owner has a linked Telegram chat and is thus eligible for Telegram (RN-TEL-03). */
    private static boolean hasTelegram(User owner) {
        return owner.getTelegramChatId() != null && !owner.getTelegramChatId().isBlank();
    }

    /**
     * Resolve the owner and require an active account with a non-blank email; otherwise skip
     * (logged, no send). A soft-deleted (RGPD) account is stored as {@link UserStatus#INACTIVE} while
     * keeping its email, so it must be filtered here to avoid mailing users who exercised their right
     * to erasure/deactivation.
     */
    private Optional<User> resolveOwner(Long ownerId) {
        if (ownerId == null) {
            return Optional.empty();
        }
        Optional<User> owner = userRepositoryPort.findById(ownerId);
        if (owner.isEmpty()) {
            log.warn("Skipping notification: owner {} not resolvable", ownerId);
            return Optional.empty();
        }
        User user = owner.get();
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("Skipping notification: owner {} is INACTIVE (RGPD soft-delete)", ownerId);
            return Optional.empty();
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping notification: owner {} has no email", ownerId);
            return Optional.empty();
        }
        return owner;
    }
}
