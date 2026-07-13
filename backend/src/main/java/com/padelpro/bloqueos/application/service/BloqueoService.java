package com.padelpro.bloqueos.application.service;

import com.padelpro.bloqueos.application.dto.BloqueoResponse;
import com.padelpro.bloqueos.domain.audit.BloqueoAuditActions;
import com.padelpro.bloqueos.domain.exception.BloqueoConflictException;
import com.padelpro.bloqueos.domain.model.BloqueoPista;
import com.padelpro.bloqueos.infrastructure.persistence.BloqueoJpaRepository;
import com.padelpro.reservas.application.service.DisponibilidadCacheInvalidator;
import com.padelpro.reservas.domain.model.ReservationOccupancy;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Application service for court blocks (change bloqueos-pista-eventos, group 2).
 *
 * <p>Wired as a plain bean (see {@code BloqueosConfig}) to keep the application layer free of Spring
 * stereotypes, mirroring {@code DisponibilidadService}.
 *
 * <h2>Create (D3) — all-or-nothing</h2>
 * A block collides when the requested slot {@code [hora, hora+60)} overlaps an <em>active</em>
 * reservation (CANCELLED never counts, RN-RES-01). If ANY requested hour collides, the whole request
 * is rejected with {@link BloqueoConflictException} (409) and nothing is inserted. Otherwise the
 * missing hours are inserted; already-blocked hours are skipped (idempotent over the {@code
 * (fecha, hora)} unique constraint).
 *
 * <p>Create and delete invalidate the {@code available-slots} cache of the affected date (D5) and
 * record an audit event (D6).
 */
public class BloqueoService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SLOT_MINUTES = 60;

    private final BloqueoJpaRepository repository;
    private final ReservationQueryPort reservationQueryPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;
    private final BloqueoAuditRecorder auditRecorder;

    public BloqueoService(BloqueoJpaRepository repository,
                          ReservationQueryPort reservationQueryPort,
                          DisponibilidadCacheInvalidator cacheInvalidator,
                          BloqueoAuditRecorder auditRecorder) {
        this.repository = repository;
        this.reservationQueryPort = reservationQueryPort;
        this.cacheInvalidator = cacheInvalidator;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Block one or more 60-min slots of {@code fecha}.
     *
     * @param fecha   the date to block
     * @param horas   slot-start hours to block (deduplicated); must be non-empty
     * @param motivo  optional reason
     * @param adminId acting admin id (for audit / {@code created_by})
     * @return the current blocks for the requested hours (existing + newly created), ordered by hour
     * @throws IllegalArgumentException   when {@code fecha} or {@code horas} is missing/empty
     * @throws BloqueoConflictException   when any requested hour overlaps an active reservation
     */
    @Transactional
    public List<BloqueoResponse> crear(LocalDate fecha, List<LocalTime> horas, String motivo, Long adminId) {
        if (fecha == null) {
            throw new IllegalArgumentException("fecha es requerida");
        }
        if (horas == null || horas.isEmpty()) {
            throw new IllegalArgumentException("horas es requerida y no puede estar vacía");
        }

        // Deduplicate while preserving order (a client may send 18:00 twice).
        Set<LocalTime> requested = new LinkedHashSet<>(horas);

        // D3 — reject the whole request if any requested slot overlaps an active reservation.
        List<ReservationOccupancy> occupancies = reservationQueryPort.findActiveOccupanciesByDate(fecha);
        List<String> conflicts = new ArrayList<>();
        for (LocalTime hora : requested) {
            LocalTime slotEnd = hora.plusMinutes(SLOT_MINUTES);
            boolean overlaps = occupancies.stream().anyMatch(occ -> occ.overlaps(hora, slotEnd));
            if (overlaps) {
                conflicts.add(hora.format(TIME_FMT));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BloqueoConflictException(
                    "No se puede bloquear: hay reservas activas en las franjas " + conflicts,
                    conflicts);
        }

        // Insert only the hours not already blocked (idempotent over UNIQUE(fecha, hora)).
        for (LocalTime hora : requested) {
            if (!repository.existsByFechaAndHora(fecha, hora)) {
                repository.save(new BloqueoPista(fecha, hora, motivo, adminId));
            }
        }

        cacheInvalidator.invalidate(fecha);
        auditRecorder.record(BloqueoAuditActions.BLOQUEO_CREATED, adminId,
                "fecha=" + fecha + ", horas=" + conflictSafeHours(requested));

        return repository.findByFechaOrderByHora(fecha).stream()
                .filter(b -> requested.contains(b.getHora()))
                .map(BloqueoResponse::from)
                .toList();
    }

    /** Blocks of a date, ordered by hour. */
    @Transactional(readOnly = true)
    public List<BloqueoResponse> listar(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("fecha es requerida");
        }
        return repository.findByFechaOrderByHora(fecha).stream()
                .map(BloqueoResponse::from)
                .toList();
    }

    /**
     * Remove a block, freeing that slot. Idempotent: a missing id is a no-op (still 204). On removal,
     * the affected date's availability cache is invalidated and the action audited.
     *
     * @param id      block id
     * @param adminId acting admin id (for audit)
     */
    @Transactional
    public void eliminar(Long id, Long adminId) {
        repository.findById(id).ifPresent(bloqueo -> {
            LocalDate fecha = bloqueo.getFecha();
            repository.delete(bloqueo);
            cacheInvalidator.invalidate(fecha);
            auditRecorder.record(BloqueoAuditActions.BLOQUEO_DELETED, adminId,
                    "id=" + id + ", fecha=" + fecha + ", hora=" + bloqueo.getHora().format(TIME_FMT));
        });
    }

    private static List<String> conflictSafeHours(Set<LocalTime> horas) {
        return horas.stream().map(h -> h.format(TIME_FMT)).toList();
    }
}
