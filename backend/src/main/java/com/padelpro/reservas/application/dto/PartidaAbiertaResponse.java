package com.padelpro.reservas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * An open match: an active reservation ({@code PENDING_CONFIRMATION}/{@code CONFIRMED}) with at least
 * one free seat, identified by its {@code reservaId} so the client can join it (capability partidas, D1).
 *
 * <p>Exposes only non-sensitive fields (RN-RGPD-03): participant display names, but no email/phone.
 * {@code priceTotal} is the informative total of the reservation (RN-RES-03); the joining cost per
 * player is derived on the client (total ÷ participants) and is NOT charged on join (D4).
 *
 * @param reservaId        reservation UUID as string
 * @param reservationDate  date of the match
 * @param startTime        start time
 * @param durationMinutes  duration in minutes
 * @param plazasLibres     free seats remaining ({@code max_participants − current participants})
 * @param priceTotal       informative total price of the reservation
 * @param participantes    current participants (display name, slot, owner flag)
 */
public record PartidaAbiertaResponse(
        String reservaId,
        LocalDate reservationDate,
        LocalTime startTime,
        Integer durationMinutes,
        int plazasLibres,
        BigDecimal priceTotal,
        List<PartidaParticipanteResponse> participantes
) {
}
