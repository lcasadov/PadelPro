package com.padelpro.notificaciones.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain event: a payment has transitioned to {@code PAID} (change
 * {@code notificaciones-eventos-email}, Req 5 / D1) — from either the Redsys webhook or an ADMIN cash
 * registration.
 *
 * <p>Published within the business transaction and consumed post-commit to email a receipt to the
 * reservation owner. The {@link #reference()} is a non-sensitive payment reference (transaction id /
 * order id) — never card data (RN-PAY-03 / RN-RGPD-04).
 *
 * @param reservationId the reservation the payment belongs to
 * @param ownerId       the reservation owner (titular) user id
 * @param amount        the paid amount
 * @param paidAt        when the payment was confirmed
 * @param reference     a non-sensitive payment reference, or {@code null} if unavailable
 */
public record PaymentPaidEmailEvent(
        UUID reservationId,
        Long ownerId,
        BigDecimal amount,
        OffsetDateTime paidAt,
        String reference) {
}
