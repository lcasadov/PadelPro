package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.PartidaAbiertaResponse;
import com.padelpro.reservas.application.dto.PartidaParticipanteResponse;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read use case for the open-matches listing (capability partidas, D1).
 *
 * <p>An "open match" is an active reservation ({@code PENDING_CONFIRMATION}/{@code CONFIRMED}) on a
 * given date with at least one free seat ({@code plazasLibres = max_participants − participantes ≥ 1}).
 * Full reservations are excluded. Only display names are projected (RN-RGPD-03) — no email/phone.
 *
 * <p>Anti-N+1: reservations are fetched with their participants in one query; payments (for the
 * informative total) and user display names are each batch-loaded once and joined in memory.
 */
public class PartidasQueryService {

    /** Active occupants (RN-RES-01): CANCELLED and COMPLETED never appear as joinable matches. */
    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.CONFIRMED);

    private final ReservationJpaRepository reservationRepository;
    private final PaymentJpaRepository paymentRepository;
    private final UserRepositoryPort userRepositoryPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;

    public PartidasQueryService(ReservationJpaRepository reservationRepository,
                                PaymentJpaRepository paymentRepository,
                                UserRepositoryPort userRepositoryPort,
                                SystemConfigRepositoryPort systemConfigRepositoryPort) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.userRepositoryPort = userRepositoryPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
    }

    /** Open matches (reservations with free seats) for {@code fecha}, ordered by start time. */
    @Transactional(readOnly = true)
    public List<PartidaAbiertaResponse> listOpenByDate(LocalDate fecha) {
        SystemConfig config = systemConfigRepositoryPort.findById(1L)
                .orElseThrow(() -> new IllegalStateException("System configuration (id=1) not found"));
        int maxParticipants = config.getMaxParticipantsPerPista();

        List<Reservation> reservations =
                reservationRepository.findActiveByDateWithParticipants(fecha, ACTIVE_STATUSES);

        // Keep only reservations that still have at least one free seat.
        List<Reservation> open = reservations.stream()
                .filter(r -> maxParticipants - r.getParticipants().size() >= 1)
                .toList();

        if (open.isEmpty()) {
            return List.of();
        }

        // Batch-load payments (informative total) for the open reservations.
        List<UUID> ids = open.stream().map(Reservation::getId).toList();
        Map<UUID, Payment> paymentsByReservation = paymentRepository.findByReservationIdIn(ids).stream()
                .collect(Collectors.toMap(Payment::getReservationId, Function.identity()));

        // Batch-load display names for every registered participant across the open reservations.
        List<Long> userIds = open.stream()
                .flatMap(r -> r.getParticipants().stream())
                .map(Participant::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> namesByUserId = userIds.isEmpty()
                ? Map.of()
                : userRepositoryPort.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, PartidasQueryService::displayName));

        return open.stream()
                .sorted(Comparator.comparing(Reservation::getStartTime))
                .map(r -> toResponse(r, maxParticipants,
                        paymentsByReservation.get(r.getId()), namesByUserId))
                .toList();
    }

    private PartidaAbiertaResponse toResponse(Reservation r, int maxParticipants,
                                              Payment payment, Map<Long, String> namesByUserId) {
        List<PartidaParticipanteResponse> participantes = r.getParticipants().stream()
                .sorted(Comparator.comparing(Participant::getSlotPosition))
                .map(p -> new PartidaParticipanteResponse(
                        participantName(p, namesByUserId), p.getSlotPosition(), p.isOwner()))
                .toList();

        BigDecimal priceTotal = payment != null ? payment.getAmount() : null;

        return new PartidaAbiertaResponse(
                r.getId().toString(),
                r.getReservationDate(),
                r.getStartTime(),
                r.getDurationMinutes(),
                maxParticipants - r.getParticipants().size(),
                priceTotal,
                participantes);
    }

    /** Display name for a participant: the guest name, or the registered user's full name. */
    private static String participantName(Participant p, Map<Long, String> namesByUserId) {
        if (p.getUserId() != null) {
            return namesByUserId.getOrDefault(p.getUserId(), "Socio");
        }
        return p.getExternalName();
    }

    private static String displayName(User u) {
        return (u.getFirstName() + " " + u.getLastName()).trim();
    }
}
