package com.padelpro.pagos.application.dto;

import java.util.UUID;

/**
 * Request body for {@code POST /api/pagos/simular} (change pagos-simulador-gestion, D2).
 *
 * <p>The card fields are used only in memory to decide the simulated outcome and are then discarded —
 * they are NEVER persisted or logged (RN-RGPD-04). {@code cardNumber} may contain spaces (normalised
 * server-side); {@code expiry} is {@code MM/AA}.
 */
public record SimularPagoRequest(
        UUID reservaId,
        String cardNumber,
        String expiry,
        String cvc
) {
}
