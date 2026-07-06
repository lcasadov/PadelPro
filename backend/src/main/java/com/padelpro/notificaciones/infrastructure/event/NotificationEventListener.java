package com.padelpro.notificaciones.infrastructure.event;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationCancelledEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.infrastructure.email.PaymentReceiptEmailTemplate;
import com.padelpro.notificaciones.infrastructure.email.ReservationCancelledEmailTemplate;
import com.padelpro.notificaciones.infrastructure.email.ReservationConfirmedEmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final UserRepositoryPort userRepositoryPort;

    public NotificationEventListener(EmailNotificationService emailNotificationService,
                                     UserRepositoryPort userRepositoryPort) {
        this.emailNotificationService = emailNotificationService;
        this.userRepositoryPort = userRepositoryPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationConfirmed(ReservationConfirmedEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            EmailMessage message = ReservationConfirmedEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.date(), event.startTime(),
                    event.durationMinutes(), event.amount());
            emailNotificationService.dispatch(message, owner.getId(),
                    ENTITY_RESERVATION, event.reservationId().toString());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelledEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            EmailMessage message = ReservationCancelledEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.reason());
            emailNotificationService.dispatch(message, owner.getId(),
                    ENTITY_RESERVATION, event.reservationId().toString());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentPaid(PaymentPaidEmailEvent event) {
        resolveOwner(event.ownerId()).ifPresent(owner -> {
            EmailMessage message = PaymentReceiptEmailTemplate.build(
                    owner.getEmail(), owner.getFirstName(), event.amount(),
                    event.paidAt(), event.reference());
            emailNotificationService.dispatch(message, owner.getId(),
                    ENTITY_PAYMENT, event.reservationId().toString());
        });
    }

    /** Resolve the owner and require a non-blank email; otherwise skip (logged, no send). */
    private Optional<User> resolveOwner(Long ownerId) {
        if (ownerId == null) {
            return Optional.empty();
        }
        Optional<User> owner = userRepositoryPort.findById(ownerId);
        if (owner.isEmpty() || owner.get().getEmail() == null || owner.get().getEmail().isBlank()) {
            log.warn("Skipping notification: owner {} not resolvable or has no email", ownerId);
            return Optional.empty();
        }
        return owner;
    }
}
