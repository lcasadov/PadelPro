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
}
