package com.padelpro.reservas.application.dto;

/**
 * Participant projection for the open-matches listing (capability partidas).
 *
 * <p>RGPD (RN-RGPD-03): only the display {@code nombre}, the {@code slotPosition} and the
 * {@code owner} flag are exposed — never email or phone.
 *
 * @param nombre       display name of the participant (registered user's full name or guest name)
 * @param slotPosition the participant's slot (1..4)
 * @param owner        whether this participant is the reservation owner
 */
public record PartidaParticipanteResponse(
        String nombre,
        Integer slotPosition,
        boolean owner
) {
}
