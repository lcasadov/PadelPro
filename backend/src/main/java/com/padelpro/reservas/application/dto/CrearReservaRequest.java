package com.padelpro.reservas.application.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/reservas} (capability reservas, US-007).
 *
 * <p>The total price is computed exclusively in the backend (RN-RES-03); any monetary value sent by
 * the client is intentionally absent from this DTO and ignored. {@code participantesAdicionales}
 * are the non-owner participants (the owner is derived from the JWT subject).
 */
public record CrearReservaRequest(
        String reservationDate,
        String startTime,
        Integer durationMinutes,
        String notes,
        List<ParticipanteAdicional> participantesAdicionales
) {
    /**
     * Additional participant: either a registered user ({@code userId}) or an external guest
     * ({@code externalName}/{@code externalPhone}). The DB enforces the XOR.
     */
    public record ParticipanteAdicional(
            Long userId,
            String externalName,
            String externalPhone
    ) {
    }
}
