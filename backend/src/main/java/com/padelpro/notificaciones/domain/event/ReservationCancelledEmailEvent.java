package com.padelpro.notificaciones.domain.event;

import java.util.UUID;

/**
 * Domain event: a reservation has transitioned to {@code CANCELLED} (change
 * {@code notificaciones-eventos-email}, Req 4 / D1).
 *
 * <p>Published within the business transaction and consumed post-commit. Only the owner (titular) is
 * notified in v1 — non-owner participants are not (spec). The cancellation reason is included only
 * when present.
 *
 * @param reservationId the cancelled reservation id
 * @param ownerId       the reservation owner (titular) user id
 * @param reason        the cancellation reason, or {@code null} if none was recorded
 */
public record ReservationCancelledEmailEvent(
        UUID reservationId,
        Long ownerId,
        String reason) {
}
