package com.padelpro.reservas.domain.model;

/**
 * Lifecycle states of a reservation (data-model §3.3, enum {@code reservation_status}).
 *
 * <p>For availability calculation (capability disponibilidad-pistas, RN-RES-01),
 * a reservation is considered an active occupant when its status is one of
 * {@link #PENDING_CONFIRMATION} or {@link #CONFIRMED}. {@link #CANCELLED} never occupies.
 */
public enum ReservationStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
