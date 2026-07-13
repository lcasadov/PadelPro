package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.bloqueos.domain.port.out.BloqueoQueryPort;
import com.padelpro.reservas.application.dto.DisponibilidadResponse;
import com.padelpro.reservas.application.dto.TramoDisponible;
import com.padelpro.reservas.domain.model.ReservationOccupancy;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Computes court availability per date (capability disponibilidad-pistas, US-006 / #13).
 *
 * <p>Implements RN-RES-01 (active reservations occupy, CANCELLED never counts), RN-RES-02
 * ({@code plazasLibres = max_participants − participantes activos que solapan el tramo}) and
 * RN-RES-04 (MANTENIMIENTO ⇒ empty list without revealing the reason).
 *
 * <h2>Opening hours / slot granularity</h2>
 * The current {@code system_config} schema (migration V6) only exposes {@code pista_state} and
 * {@code max_participants_per_pista} — it has no opening/closing/granularity columns. Until those
 * columns exist, the club schedule is fixed by the documented defaults below
 * (open {@value #OPEN_HOUR}:00, close {@value #CLOSE_HOUR}:00, {@value #SLOT_MINUTES}-minute slots),
 * aligned with the {@code duracionMinutos: 60} example in {@code docs/openapi.yaml}.
 *
 * <h2>Caching</h2>
 * Results are cached for 30 s keyed by {@code fecha} (cache {@code available-slots}, data-model §7.3).
 * {@link #invalidate(LocalDate)} is the reusable eviction hook Wave 3 must call on write.
 *
 * <p>Wired explicitly as a bean (see {@code DisponibilidadConfig}) to keep the application layer
 * free of Spring stereotypes, mirroring {@code SystemConfigService}.
 */
public class DisponibilidadService implements DisponibilidadCacheInvalidator {

    /** Club opening hour (inclusive). Default until {@code system_config} exposes it. */
    static final int OPEN_HOUR = 8;
    /** Club closing hour (exclusive end of the last slot). Default until {@code system_config} exposes it. */
    static final int CLOSE_HOUR = 23;
    /** Slot granularity in minutes. Default until {@code system_config} exposes it. */
    static final int SLOT_MINUTES = 60;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationQueryPort reservationQueryPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final BloqueoQueryPort bloqueoQueryPort;

    public DisponibilidadService(ReservationQueryPort reservationQueryPort,
                                 SystemConfigRepositoryPort systemConfigRepositoryPort,
                                 BloqueoQueryPort bloqueoQueryPort) {
        this.reservationQueryPort = reservationQueryPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.bloqueoQueryPort = bloqueoQueryPort;
    }

    /**
     * Compute the available slots for {@code fecha}.
     *
     * @param fecha the date to query (already parsed/validated by the controller)
     * @return the response with only slots that have at least one free seat; an empty list when the
     *         court is in MANTENIMIENTO
     */
    @Cacheable(value = "available-slots", key = "#fecha")
    public DisponibilidadResponse getDisponibilidad(LocalDate fecha) {
        SystemConfig config = systemConfigRepositoryPort.findById(1L)
                .orElseThrow(() -> new IllegalStateException("System configuration (id=1) not found"));

        String fechaStr = fecha.toString();

        // RN-RES-04 — court under maintenance: empty list, no reason exposed.
        if (config.getPistaState() == PistaState.MANTENIMIENTO) {
            return new DisponibilidadResponse(fechaStr, List.of());
        }

        int maxParticipants = config.getMaxParticipantsPerPista();
        List<ReservationOccupancy> occupancies = reservationQueryPort.findActiveOccupanciesByDate(fecha);
        // D4 — blocked slots are dropped from the offered tramos without revealing the reason.
        Set<LocalTime> horasBloqueadas = bloqueoQueryPort.findHorasBloqueadasByFecha(fecha);

        List<TramoDisponible> tramos = new ArrayList<>();
        for (int hour = OPEN_HOUR; hour < CLOSE_HOUR; hour++) {
            LocalTime slotStart = LocalTime.of(hour, 0);
            LocalTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);

            // A blocked slot is not offered at all (neither creatable nor joinable).
            if (horasBloqueadas.contains(slotStart)) {
                continue;
            }

            int occupied = 0;
            for (ReservationOccupancy occ : occupancies) {
                if (occ.overlaps(slotStart, slotEnd)) {
                    occupied += occ.participantCount();
                }
            }

            int plazasLibres = maxParticipants - occupied;
            if (plazasLibres >= 1) {
                // creable (D7): true iff the slot is fully empty (occupied == 0, i.e. plazasLibres == maxParticipants);
                // a slot with an incomplete reservation is only joinable, not creatable.
                boolean creable = occupied == 0;
                tramos.add(new TramoDisponible(slotStart.format(TIME_FMT), SLOT_MINUTES, plazasLibres, creable));
            }
        }

        return new DisponibilidadResponse(fechaStr, tramos);
    }

    /**
     * Reusable invalidation hook (D6). Evicts the cached availability for {@code fecha}.
     * No-op behaviour beyond eviction; intended to be called by Wave 3 on reservation write.
     */
    @Override
    @CacheEvict(value = "available-slots", key = "#fecha")
    public void invalidate(LocalDate fecha) {
        // Eviction handled by @CacheEvict; method body intentionally empty.
    }
}
