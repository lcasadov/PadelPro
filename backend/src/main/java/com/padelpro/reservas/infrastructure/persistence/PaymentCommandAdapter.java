package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Write adapter for payments (capability reservas, US-007). One payment per reservation (D3).
 */
@Component
public class PaymentCommandAdapter implements PaymentCommandPort {

    private final PaymentJpaRepository paymentRepository;

    public PaymentCommandAdapter(PaymentJpaRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Override
    public Optional<Payment> findByReservationId(UUID reservationId) {
        return paymentRepository.findByReservationId(reservationId);
    }
}
