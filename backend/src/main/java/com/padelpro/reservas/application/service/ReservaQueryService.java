package com.padelpro.reservas.application.service;

import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read use cases for reservations (capability reservas, US-007, RN-AUTH-01).
 *
 * <p>Anti-N+1 (data-model §7.2): reservations are fetched with their participants in a single query,
 * then payments are batch-loaded once by reservation id and joined in memory — never one query per
 * reservation.
 *
 * <p>BOLA prevention: a USER who is neither owner nor participant gets 403 (never 404) on detail.
 */
public class ReservaQueryService {

    private final ReservationJpaRepository reservationRepository;
    private final PaymentJpaRepository paymentRepository;

    public ReservaQueryService(ReservationJpaRepository reservationRepository,
                               PaymentJpaRepository paymentRepository) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Reservations where the user is owner or participant (RN-AUTH-01). */
    @Transactional(readOnly = true)
    public List<ReservaResponse> listForUser(Long userId) {
        List<Reservation> reservations =
                reservationRepository.findVisibleToUserWithParticipants(userId);
        // RN-RGPD-03 — a co-participant (not owner) must not see guest phones / notes.
        return mapWithPayments(reservations, false, userId);
    }

    /** All reservations (ADMIN). */
    @Transactional(readOnly = true)
    public List<ReservaResponse> listAll() {
        return mapWithPayments(reservationRepository.findAllWithParticipants(), true, null);
    }

    /**
     * Reservation detail with access control. ADMIN may read any; a USER may read only if owner or
     * participant, otherwise 403 (BOLA prevention — never reveal existence with 404).
     */
    @Transactional(readOnly = true)
    public ReservaResponse getForUser(UUID id, Long userId, boolean admin) {
        Reservation reservation = reservationRepository.findByIdWithParticipants(id)
                .orElseThrow(() -> new ReservaForbiddenException(
                        "No tiene acceso a esta reserva"));
        if (!admin && !isOwnerOrParticipant(reservation, userId)) {
            throw new ReservaForbiddenException("No tiene acceso a esta reserva");
        }
        Payment payment = paymentRepository.findByReservationId(id).orElse(null);
        // RN-RGPD-03 — only owner and ADMIN see guest phones / notes; a co-participant does not.
        boolean includePii = admin || userId.equals(reservation.getOwnerId());
        return ReservaMapper.toResponse(reservation, payment, includePii);
    }

    private boolean isOwnerOrParticipant(Reservation reservation, Long userId) {
        if (userId.equals(reservation.getOwnerId())) {
            return true;
        }
        return reservation.getParticipants().stream()
                .map(Participant::getUserId)
                .anyMatch(userId::equals);
    }

    /**
     * @param adminView   when {@code true} (ADMIN listing) every reservation exposes full PII
     * @param requesterId requesting user id (null for ADMIN); PII is only exposed for reservations
     *                   they own (RN-RGPD-03) — a co-participant gets guest phones / notes nulled
     */
    private List<ReservaResponse> mapWithPayments(List<Reservation> reservations,
                                                  boolean adminView, Long requesterId) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = reservations.stream().map(Reservation::getId).toList();
        Map<UUID, Payment> paymentsByReservation = paymentRepository.findByReservationIdIn(ids).stream()
                .collect(Collectors.toMap(Payment::getReservationId, Function.identity()));
        return reservations.stream()
                .map(r -> {
                    boolean includePii = adminView
                            || (requesterId != null && requesterId.equals(r.getOwnerId()));
                    return ReservaMapper.toResponse(r, paymentsByReservation.get(r.getId()), includePii);
                })
                .toList();
    }
}
