package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.model.IdempotencyKey;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PriceCalculator;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Create-reservation use case (capability reservas, US-007, RN-RES-01/03/05).
 *
 * <p>Flow: validate input (400) → idempotency short-circuit (D2) → atomically persist reservation
 * (PENDING_CONFIRMATION) + participants (owner slot 1) + payment (PENDING, frozen amount, D3) →
 * gist overlap → 409 (D1) → record idempotency key → invalidate availability cache for the date.
 */
public class CrearReservaService {

    /** Strict ISO date parser (rejects 2025-13-40, non-ISO formats). */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(60, 90, 120, 150, 180);

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;

    public CrearReservaService(ReservationCommandPort reservationCommandPort,
                               PaymentCommandPort paymentCommandPort,
                               SystemConfigRepositoryPort systemConfigRepositoryPort,
                               UserRepositoryPort userRepositoryPort,
                               DisponibilidadCacheInvalidator cacheInvalidator) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * @param ownerId        authenticated user id (JWT subject) — always the reservation owner
     * @param idempotencyKey optional {@code Idempotency-Key} header value (D2)
     */
    @Transactional
    public ReservaResponse crear(Long ownerId, CrearReservaRequest request, String idempotencyKey) {
        // D2 — idempotency short-circuit (before any validation side effects matter).
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyKey> existing =
                    reservationCommandPort.findIdempotencyKey(ownerId, idempotencyKey.trim());
            if (existing.isPresent()) {
                return loadExisting(existing.get().getReservationId());
            }
        }

        LocalDate reservationDate = parseDate(request);
        LocalTime startTime = parseTime(request);
        int duration = validateDuration(request);
        validateFuture(reservationDate, startTime);

        SystemConfig config = loadConfig();
        validateParticipantsLimit(request, config.getMaxParticipantsPerPista());

        Reservation reservation = Reservation.create(
                ownerId, reservationDate, startTime, duration, ReservationChannel.WEB, request.notes());
        reservation.addParticipant(Participant.owner(ownerId, ReservationChannel.WEB));
        addAdditionalParticipants(reservation, request);

        // Persist reservation (+ participants via cascade). gist overlap → 409 here (D1).
        Reservation saved = reservationCommandPort.save(reservation);

        // D3 — atomic payment with frozen amount (price_per_hour × duration / 60).
        BigDecimal amount = PriceCalculator.calculate(config.getPricePerHour(), duration);
        Payment payment = paymentCommandPort.save(Payment.pendingFor(saved.getId(), amount));

        // D2 — record idempotency key after successful creation.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            reservationCommandPort.saveIdempotencyKey(
                    new IdempotencyKey(idempotencyKey.trim(), ownerId, saved.getId()));
        }

        // Invalidate availability cache for the affected date (D5 / data-model §7.3).
        cacheInvalidator.invalidate(reservationDate);

        return ReservaMapper.toResponse(saved, payment);
    }

    private ReservaResponse loadExisting(java.util.UUID reservationId) {
        Reservation r = reservationCommandPort.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency key references a missing reservation: " + reservationId));
        Payment payment = paymentCommandPort.findByReservationId(reservationId).orElse(null);
        return ReservaMapper.toResponse(r, payment);
    }

    private SystemConfig loadConfig() {
        return systemConfigRepositoryPort.findById(1L)
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
    }

    private void addAdditionalParticipants(Reservation reservation, CrearReservaRequest request) {
        List<CrearReservaRequest.ParticipanteAdicional> extras = request.participantesAdicionales();
        if (extras == null || extras.isEmpty()) {
            return;
        }
        int slot = 2; // owner occupies slot 1
        for (CrearReservaRequest.ParticipanteAdicional p : extras) {
            boolean hasUser = p.userId() != null;
            boolean hasExternal = p.externalName() != null && !p.externalName().isBlank();
            if (hasUser == hasExternal) {
                throw new ValidationException(
                        "Cada participante adicional debe ser un usuario registrado o un invitado externo, no ambos ni ninguno");
            }
            if (hasUser) {
                // Security: a registered participant must reference a real, ACTIVE member. Without this
                // check a caller could attach an arbitrary or forged userId (or an inactive account) to
                // their reservation.
                if (!userRepositoryPort.existsByIdAndStatus(p.userId(), UserStatus.ACTIVE)) {
                    throw new InvalidReservaStateException(
                            "PARTICIPANT_NOT_FOUND",
                            "El participante registrado no existe o no es un usuario activo");
                }
                reservation.addParticipant(
                        Participant.registered(p.userId(), slot, ReservationChannel.WEB));
            } else {
                reservation.addParticipant(
                        Participant.external(p.externalName(), p.externalPhone(), slot, ReservationChannel.WEB));
            }
            slot++;
        }
    }

    // ── validations ───────────────────────────────────────────────────────────

    private LocalDate parseDate(CrearReservaRequest request) {
        if (request.reservationDate() == null || request.reservationDate().isBlank()) {
            throw new ValidationException("reservationDate es obligatorio (formato YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(request.reservationDate().trim(), DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("reservationDate debe tener el formato YYYY-MM-DD");
        }
    }

    private LocalTime parseTime(CrearReservaRequest request) {
        if (request.startTime() == null || request.startTime().isBlank()) {
            throw new ValidationException("startTime es obligatorio (formato HH:mm)");
        }
        LocalTime time;
        try {
            time = LocalTime.parse(request.startTime().trim(), TIME_FMT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("startTime debe tener el formato HH:mm");
        }
        if (time.getMinute() != 0 && time.getMinute() != 30) {
            throw new ValidationException("startTime debe ser en punto o en media hora (minutos 00 o 30)");
        }
        return time;
    }

    private int validateDuration(CrearReservaRequest request) {
        Integer duration = request.durationMinutes();
        if (duration == null || !ALLOWED_DURATIONS.contains(duration)) {
            throw new ValidationException(
                    "durationMinutes debe ser uno de 60, 90, 120, 150 o 180");
        }
        return duration;
    }

    private void validateFuture(LocalDate date, LocalTime startTime) {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(date, startTime);
        if (!start.isAfter(java.time.LocalDateTime.now())) {
            throw new ValidationException("La fecha y hora de la reserva deben estar en el futuro");
        }
    }

    private void validateParticipantsLimit(CrearReservaRequest request, int maxParticipants) {
        int extras = request.participantesAdicionales() == null
                ? 0 : request.participantesAdicionales().size();
        int total = 1 + extras; // owner + additional
        if (total > maxParticipants) {
            throw new InvalidReservaStateException(
                    "PARTICIPANTS_LIMIT_EXCEEDED",
                    "La reserva excede el máximo de " + maxParticipants + " participantes");
        }
    }
}
