package com.padelpro.reservas.application.service;

import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Abandon-a-match use case (capability partidas, D3).
 *
 * <p>A non-owner participant may leave a reservation they joined, freeing their seat (the participant
 * row is deleted via {@code orphanRemoval}). The owner CANNOT abandon — they must cancel the
 * reservation through the existing cancellation flow (422). A user who is not a participant gets 422.
 */
public class AbandonarReservaService {

    private final ReservationCommandPort reservationCommandPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;

    public AbandonarReservaService(ReservationCommandPort reservationCommandPort,
                                   DisponibilidadCacheInvalidator cacheInvalidator) {
        this.reservationCommandPort = reservationCommandPort;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * @param reservaId reservation to leave
     * @param userId    authenticated user id (JWT subject)
     */
    @Transactional
    public void abandonar(UUID reservaId, Long userId) {
        Reservation reservation = reservationCommandPort.findById(reservaId)
                .orElseThrow(() -> new ReservaNotFoundException(reservaId));

        // Abandoning only makes sense while the reservation is active (D3).
        if (!reservation.isActiveOccupant()) {
            throw new InvalidReservaStateException(
                    "RESERVA_NOT_JOINABLE",
                    "La reserva no está activa; no es posible abandonarla");
        }

        Participant participant = reservation.getParticipants().stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findFirst()
                .orElseThrow(() -> new InvalidReservaStateException(
                        "NOT_A_PARTICIPANT", "El usuario no participa en esta reserva"));

        // The owner cannot leave: they must cancel the reservation instead (D3).
        if (participant.isOwner()) {
            throw new InvalidReservaStateException(
                    "OWNER_CANNOT_ABANDON",
                    "El titular no puede abandonar la reserva; debe cancelarla");
        }

        reservation.removeParticipant(participant);
        reservationCommandPort.save(reservation); // orphanRemoval → DELETE the participant row

        cacheInvalidator.invalidate(reservation.getReservationDate());
    }
}
