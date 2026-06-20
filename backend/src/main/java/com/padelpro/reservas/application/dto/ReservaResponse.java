package com.padelpro.reservas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reservation view returned by the create / detail / list endpoints (capability reservas, US-007).
 *
 * <p>{@code priceTotal} is the backend-computed total (== the payment amount, RN-RES-03).
 */
public record ReservaResponse(
        String id,
        Long ownerId,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer durationMinutes,
        String status,
        String channel,
        String notes,
        BigDecimal priceTotal,
        List<ParticipanteResponse> participants,
        PagoResponse pago,
        OffsetDateTime createdAt
) {
}
