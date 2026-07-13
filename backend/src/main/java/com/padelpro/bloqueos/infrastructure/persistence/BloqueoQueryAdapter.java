package com.padelpro.bloqueos.infrastructure.persistence;

import com.padelpro.bloqueos.domain.port.out.BloqueoQueryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Outbound adapter implementing {@link BloqueoQueryPort} on top of {@link BloqueoJpaRepository}
 * (change bloqueos-pista-eventos, D4). Returns the blocked slot-start hours of a date as a set for
 * O(1) membership checks in the availability loop.
 */
@Component
public class BloqueoQueryAdapter implements BloqueoQueryPort {

    private final BloqueoJpaRepository repository;

    public BloqueoQueryAdapter(BloqueoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalTime> findHorasBloqueadasByFecha(LocalDate fecha) {
        return new HashSet<>(repository.findHorasByFecha(fecha));
    }
}
