package com.padelpro.reservas.domain.model;

/**
 * Payment lifecycle states (data-model §3.5, enum {@code payment_status}).
 *
 * <p>A payment is born {@link #PENDING} together with its reservation (D3). The MVP confirms
 * via ADMIN; refunds (cancellation within the deadline) move a {@link #PAID} payment to
 * {@link #REFUNDED}.
 */
public enum PaymentStatus {
    PENDING,
    IN_PROGRESS,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED
}
