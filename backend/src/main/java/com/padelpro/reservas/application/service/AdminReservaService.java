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
import com.padelpro.notificaciones.domain.event.ReservationCancelledEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ApplicationEventPublisher eventPublisher;

    public AdminReservaService(ReservationCommandPort reservationCommandPort,
                               PaymentCommandPort paymentCommandPort,
                               DisponibilidadCacheInvalidator cacheInvalidator,
                               ApplicationEventPublisher eventPublisher) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.cacheInvalidator = cacheInvalidator;
        this.eventPublisher = eventPublisher;
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

        // Notification triggers (change notificaciones-eventos-email, D1): published inside the tx and
        // delivered by an AFTER_COMMIT listener, so a rolled-back transition sends no email.
        if (target == ReservationStatus.CONFIRMED) {
            BigDecimal amount = payment != null ? payment.getAmount() : null;
            eventPublisher.publishEvent(new ReservationConfirmedEmailEvent(
                    saved.getId(), saved.getOwnerId(), saved.getReservationDate(),
                    saved.getStartTime(), saved.getDurationMinutes(), amount));
        } else if (target == ReservationStatus.CANCELLED) {
            eventPublisher.publishEvent(new ReservationCancelledEmailEvent(
                    saved.getId(), saved.getOwnerId(), saved.getCancellationReason()));
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
