package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.UnirseResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ParticipacionDuplicadaException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.port.out.ParticipantCommandPort;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Join-a-match use case (capability partidas, D2, RN-AUTH-03 / RN-RES-02).
 *
 * <p>Adds the authenticated user as a non-owner participant of an existing reservation. Validation
 * and insertion are atomic: the reservation row is loaded {@code FOR UPDATE} so two concurrent joins
 * to the last seat serialise — exactly one succeeds (200), the other sees the reservation full (422).
 *
 * <p>Joining does NOT create a per-participant payment nor charge online (D4); the returned
 * {@code statusPago} is the reservation's existing payment status, purely informative.
 */
public class UnirseReservaService {

    private final ReservationCommandPort reservationCommandPort;
    private final ParticipantCommandPort participantCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;

    public UnirseReservaService(ReservationCommandPort reservationCommandPort,
                                ParticipantCommandPort participantCommandPort,
                                PaymentCommandPort paymentCommandPort,
                                SystemConfigRepositoryPort systemConfigRepositoryPort,
                                UserRepositoryPort userRepositoryPort,
                                DisponibilidadCacheInvalidator cacheInvalidator) {
        this.reservationCommandPort = reservationCommandPort;
        this.participantCommandPort = participantCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * @param reservaId reservation to join
     * @param userId    authenticated user id (JWT subject) — becomes a non-owner participant
     */
    @Transactional
    public UnirseResponse unirse(UUID reservaId, Long userId) {
        // Lock the reservation row (SELECT ... FOR UPDATE) so the seat check + insert are atomic (D2).
        Reservation reservation = reservationCommandPort.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new ReservaNotFoundException(reservaId));

        // Only PENDING_CONFIRMATION / CONFIRMED admit new participants (CANCELLED/COMPLETED → 422).
        if (!reservation.isActiveOccupant()) {
            throw new InvalidReservaStateException(
                    "RESERVA_NOT_JOINABLE",
                    "La reserva no admite nuevos participantes en su estado actual");
        }

        // RN-AUTH-03 — a user cannot join twice (owner counts as a participant).
        boolean alreadyParticipant = reservation.getParticipants().stream()
                .map(Participant::getUserId)
                .anyMatch(userId::equals);
        if (alreadyParticipant) {
            throw new ParticipacionDuplicadaException("El usuario ya participa en esta reserva");
        }

        // RN-RES-02 — reject when there are no free seats. Read AFTER acquiring the lock so a
        // concurrent join that just committed is already reflected in the participant count.
        int maxParticipants = loadMaxParticipants();
        if (reservation.getParticipants().size() >= maxParticipants) {
            throw new InvalidReservaStateException(
                    "PARTICIPANTS_LIMIT_EXCEEDED",
                    "La reserva ya ha alcanzado el máximo de " + maxParticipants + " participantes");
        }

        int slot = reservation.nextSlotPosition();
        Participant participant = Participant.registered(userId, slot, ReservationChannel.WEB);
        reservation.addParticipant(participant); // wires the reservation association (FK)
        // INSERT the single participant directly so its generated id is populated (a whole-aggregate
        // merge would leave this reference id-less). The row is inserted while the reservation is still
        // locked FOR UPDATE, so the seat count is enforced atomically against concurrent joins.
        Participant saved = participantCommandPort.save(participant);

        // Availability cache would otherwise serve the just-taken seat as free (D6 / §7.3).
        cacheInvalidator.invalidate(reservation.getReservationDate());

        String nombre = resolveName(userId);
        String statusPago = resolvePaymentStatus(reservaId);

        return new UnirseResponse(
                saved.getId(), reservaId.toString(), userId, nombre, statusPago);
    }

    private int loadMaxParticipants() {
        SystemConfig config = systemConfigRepositoryPort.findById(1L)
                .orElseThrow(() -> new IllegalStateException("System configuration (id=1) not found"));
        return config.getMaxParticipantsPerPista();
    }

    private String resolveName(Long userId) {
        return userRepositoryPort.findById(userId)
                .map(this::displayName)
                .orElse("Socio");
    }

    private String displayName(User u) {
        return (u.getFirstName() + " " + u.getLastName()).trim();
    }

    private String resolvePaymentStatus(UUID reservaId) {
        return paymentCommandPort.findByReservationId(reservaId)
                .map(Payment::getStatus)
                .map(Enum::name)
                .orElse(PaymentStatus.PENDING.name());
    }
}
