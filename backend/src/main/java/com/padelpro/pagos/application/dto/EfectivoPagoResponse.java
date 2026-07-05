package com.padelpro.pagos.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response of {@code POST /api/admin/pagos/{reservaId}/efectivo} (pagos-redsys-online, group 5).
 */
public record EfectivoPagoResponse(
        String pagoId,
        String reservaId,
        BigDecimal amount,
        String method,
        String status,
        Long registeredById,
        OffsetDateTime paidAt
) {
}
