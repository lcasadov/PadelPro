package com.padelpro.administracion.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Income metric for {@code GET /api/admin/dashboard/ingresos} (capability administracion-club,
 * RN-ADM-03).
 *
 * <p>{@code total = SUM(payments.amount)} where {@code status = PAID} and {@code paid_at} falls in the
 * requested range; {@code porMetodo} breaks the total down by {@code method} and always carries the
 * {@code REDSYS} and {@code CASH} keys (0.00 when a method has no payments). A range without PAID
 * payments returns {@code total = 0.00} and both methods at {@code 0.00} with HTTP 200.
 *
 * @param fechaInicio inclusive start of the queried range
 * @param fechaFin    inclusive end of the queried range
 * @param total       total income in EUR (2 decimals)
 * @param porMetodo   income per payment method, keys {@code REDSYS} and {@code CASH}
 */
public record IngresosResponse(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        BigDecimal total,
        Map<String, BigDecimal> porMetodo
) {
}
