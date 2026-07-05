package com.padelpro.reservas.domain.port.out;

import com.padelpro.reservas.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound write port for payments (capability reservas, US-007). One payment per reservation (D3).
 */
public interface PaymentCommandPort {

    Payment save(Payment payment);

    Optional<Payment> findByReservationId(UUID reservationId);

    /** Locate a payment by its unique Redsys order id (webhook path, pagos-redsys-online). */
    Optional<Payment> findByRedsysOrderId(String redsysOrderId);

    /**
     * Locate a payment by its Redsys order id acquiring a pessimistic write lock on the row
     * ({@code SELECT ... FOR UPDATE}). Used by the webhook to serialise concurrent notifications for
     * the same order so idempotency holds under a race (MEDIO-2, pagos-redsys-online). Must be called
     * inside a transaction; the second notification blocks until the first commits and then re-reads
     * the already-{@code PAID} state.
     */
    Optional<Payment> findByRedsysOrderIdForUpdate(String redsysOrderId);
}
