package com.padelpro.reservas.application.dto;

/**
 * Participant view in a reservation response (capability reservas, US-007).
 */
public record ParticipanteResponse(
        Long userId,
        String externalName,
        String externalPhone,
        Integer slotPosition,
        boolean owner
) {
}
