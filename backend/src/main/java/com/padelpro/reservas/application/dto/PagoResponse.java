package com.padelpro.reservas.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payment view embedded in a reservation response (data-model §3.5).
 * The {@code amount} is the frozen total (RN-RES-03).
 */
public record PagoResponse(
        String id,
        BigDecimal amount,
        String method,
        String status,
        OffsetDateTime paidAt
) {
}
