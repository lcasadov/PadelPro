package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStateMachine;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin reservation management (capability reservas, US-007, D-RES-03).
 *
 * <p>{@code PATCH /api/admin/reservas/{id}/estado}: the ADMIN drives the unidirectional state
 * machine (D6) and bypasses the cancellation deadline policy. Invalid transitions → 422. On
 * CANCELLED a PAID payment is refunded; in the CASH/MVP path CONFIRMED leaves the payment PENDING
 * (D5 — cash is collected/registered separately).
 */
public class AdminReservaService {

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;

    public AdminReservaService(ReservationCommandPort reservationCommandPort,
                               PaymentCommandPort paymentCommandPort,
                               DisponibilidadCacheInvalidator cacheInvalidator) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * Change a reservation's state as ADMIN.
     *
     * @param id        reservation id
     * @param newStatus target status (parsed from the request body)
     */
    @Transactional
    public ReservaResponse cambiarEstado(UUID id, String newStatus) {
        ReservationStatus target = parseStatus(newStatus);

        Reservation reservation = reservationCommandPort.findById(id)
                .orElseThrow(() -> new ReservaNotFoundException(id));

        // D6 — only valid transitions; invalid ones (incl. terminal/no-op) → 422.
        ReservationStateMachine.assertCanTransition(reservation.getStatus(), target);

        reservation.changeStatus(target);
        Reservation saved = reservationCommandPort.save(reservation);

        Payment payment = paymentCommandPort.findByReservationId(id).orElse(null);
        if (target == ReservationStatus.CANCELLED && payment != null
                && payment.getStatus() == PaymentStatus.PAID) {
            payment.markRefunded();
            payment = paymentCommandPort.save(payment);
        }

        // Availability changes when a reservation leaves the active set (CANCELLED/COMPLETED).
        if (target == ReservationStatus.CANCELLED || target == ReservationStatus.COMPLETED) {
            cacheInvalidator.invalidate(reservation.getReservationDate());
        }

        // ADMIN path → full PII.
        return ReservaMapper.toResponse(saved, payment, true);
    }

    private ReservationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("status es obligatorio");
        }
        try {
            return ReservationStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("status no es un estado de reserva válido: " + raw);
        }
    }
}
