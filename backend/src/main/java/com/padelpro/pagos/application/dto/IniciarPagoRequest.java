package com.padelpro.pagos.application.dto;

import java.util.UUID;

/**
 * Request body for {@code POST /api/pagos/iniciar} (pagos-redsys-online, D5).
 *
 * <p>Only {@code reservaId} is meaningful — any amount sent by the client is ignored; the charged
 * amount is always the backend-frozen {@code payments.amount} (RN-RES-03).
 */
public record IniciarPagoRequest(UUID reservaId) {
}
