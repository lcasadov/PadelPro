package com.padelpro.bloqueos.domain.port.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Outbound port exposing the blocked hours of a date (change bloqueos-pista-eventos, D4).
 *
 * <p>Consumed by the availability calculation to drop blocked slots from the offered tramos, without
 * revealing the reason. Returns only the slot-start times ({@code LocalTime}) so the caller stays free
 * of the {@code bloqueo_pista} entity.
 */
public interface BloqueoQueryPort {

    /**
     * Blocked slot-start hours for the given date.
     *
     * @param fecha the date to query
     * @return set of blocked hours (never {@code null}); empty when nothing is blocked that date
     */
    Set<LocalTime> findHorasBloqueadasByFecha(LocalDate fecha);
}
