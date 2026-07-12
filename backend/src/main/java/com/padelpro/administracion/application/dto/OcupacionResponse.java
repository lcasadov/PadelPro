package com.padelpro.administracion.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Occupancy metric for {@code GET /api/admin/dashboard/ocupacion} (capability administracion-club,
 * RN-ADM-02).
 *
 * <p>{@code ocupacionPct = slotsReservados / slotsDisponibles * 100} (0..100, one decimal). When the
 * range has no available slots (schedule not configured) both {@code slotsDisponibles} and
 * {@code ocupacionPct} are {@code 0} — no division by zero, HTTP 200.
 *
 * @param fechaInicio      inclusive start of the queried range
 * @param fechaFin         inclusive end of the queried range
 * @param slotsReservados  reserved slots (non-cancelled reservations, RN-ADM-02)
 * @param slotsDisponibles available slots derived from the club opening hours in {@code system_config}
 * @param ocupacionPct     occupancy percentage in [0, 100] with a single decimal
 */
public record OcupacionResponse(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        int slotsReservados,
        int slotsDisponibles,
        BigDecimal ocupacionPct
) {
}
