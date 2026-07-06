package com.padelpro.notificaciones.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Domain event: a reservation has transitioned to {@code CONFIRMED} (change
 * {@code notificaciones-eventos-email}, Req 1 / D1).
 *
 * <p>Published from the reservation application service <em>within</em> the business transaction and
 * consumed by a {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so the confirmation email is
 * only sent if the transaction actually committed (RN-NOT-01). Carries a snapshot of the display data
 * so the listener needs no further reads; the owner's email is resolved from {@link #ownerId()}.
 *
 * @param reservationId   the confirmed reservation id
 * @param ownerId         the reservation owner (titular) user id
 * @param date            reservation date
 * @param startTime       reservation start time
 * @param durationMinutes reservation duration in minutes
 * @param amount          reservation amount, or {@code null} if no payment is associated
 */
public record ReservationConfirmedEmailEvent(
        UUID reservationId,
        Long ownerId,
        LocalDate date,
        LocalTime startTime,
        Integer durationMinutes,
        BigDecimal amount) {
}
