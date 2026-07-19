package com.padelpro.reservas.application.service;

import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStateMachine;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Confirm-reservation use case for the USER/owner path (capability bot-telegram-reservas, D-4;
 * capability reservas, US-007, RN-AUTH-02).
 *
 * <p>This is the owner-facing twin of the ADMIN confirmation embedded in
 * {@link AdminReservaService#cambiarEstado}: it drives the same {@code PENDING_CONFIRMATION →
 * CONFIRMED} transition through the shared {@link ReservationStateMachine} (D6) and publishes the
 * same {@link ReservationConfirmedEmailEvent} (change {@code notificaciones-eventos-email}, D1), but
 * enforces ownership (RN-AUTH-02) instead of the ADMIN bypass. It exists so the Telegram dispatcher
 * never reimplements reservation business rules (design D-3).
 *
 * <p>Confirming does not change availability (a {@code PENDING_CONFIRMATION} reservation already
 * occupies its slot, {@link Reservation#isActiveOccupant()}), so the availability cache is left
 * untouched — mirroring the ADMIN CONFIRMED branch.
 */
public class ConfirmarReservaService {

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final ApplicationEventPublisher eventPublisher;

    public ConfirmarReservaService(ReservationCommandPort reservationCommandPort,
                                   PaymentCommandPort paymentCommandPort,
                                   ApplicationEventPublisher eventPublisher) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Confirm a pending reservation owned by {@code userId}.
     *
     * @param id     reservation id
     * @param userId authenticated user id (the Telegram-resolved account)
     * @throws ReservaNotFoundException  if the reservation does not exist
     * @throws ReservaForbiddenException if the caller is not the owner (RN-AUTH-02)
     * @throws com.padelpro.reservas.domain.exception.InvalidReservaStateException
     *         if the reservation is not in a confirmable state (422, via the state machine)
     */
    @Transactional
    public void confirmar(UUID id, Long userId) {
        Reservation reservation = reservationCommandPort.findById(id)
                .orElseThrow(() -> new ReservaNotFoundException(id));

        // RN-AUTH-02 — only the owner may confirm through this path (ADMIN uses AdminReservaService).
        if (!userId.equals(reservation.getOwnerId())) {
            throw new ReservaForbiddenException("Solo el titular puede confirmar la reserva");
        }

        // D6 — only a valid transition; a terminal/confirmed reservation → 422.
        ReservationStateMachine.assertCanTransition(reservation.getStatus(), ReservationStatus.CONFIRMED);

        reservation.changeStatus(ReservationStatus.CONFIRMED);
        Reservation saved = reservationCommandPort.save(reservation);

        // Notification trigger (D1): published inside the tx, delivered AFTER_COMMIT — a rolled-back
        // confirmation sends no email. Carries a snapshot so the listener needs no further reads.
        Payment payment = paymentCommandPort.findByReservationId(id).orElse(null);
        BigDecimal amount = payment != null ? payment.getAmount() : null;
        eventPublisher.publishEvent(new ReservationConfirmedEmailEvent(
                saved.getId(), saved.getOwnerId(), saved.getReservationDate(),
                saved.getStartTime(), saved.getDurationMinutes(), amount));
    }
}
