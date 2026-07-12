package com.padelpro.pagos.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Payment history row for {@code GET /api/pagos} (own) and {@code GET /api/admin/pagos} (all)
 * (pagos-redsys-online, group 5). Never exposes card data — only {@code redsysOrderId} and
 * {@code transactionId} (RN-PAY-03).
 */
public record PagoHistorialResponse(
        String pagoId,
        String reservaId,
        Long ownerId,
        String ownerName,
        BigDecimal amount,
        String method,
        String status,
        String redsysOrderId,
        String transactionId,
        LocalDate reservationDate,
        LocalTime startTime,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt
) {
}
