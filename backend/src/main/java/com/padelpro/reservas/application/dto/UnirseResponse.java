package com.padelpro.reservas.application.dto;

/**
 * Confirmation returned by {@code POST /api/reservas/{id}/unirse} (capability partidas, D2).
 *
 * <p>Joining does NOT charge online (D4): {@code statusPago} reflects the reservation's existing
 * payment status (informative), not a per-participant charge.
 *
 * @param participanteId id of the created participant row
 * @param reservaId      reservation UUID as string
 * @param userId         id of the user who joined
 * @param nombre         display name of the user who joined
 * @param statusPago     informative payment status of the reservation (e.g. {@code PENDING})
 */
public record UnirseResponse(
        Long participanteId,
        String reservaId,
        Long userId,
        String nombre,
        String statusPago
) {
}
