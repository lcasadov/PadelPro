package com.padelpro.pagos.application.dto;

/**
 * Result of {@code POST /api/pagos/simular} (change pagos-simulador-gestion, D2).
 *
 * <p>{@code resultado} is {@code "APPROVED"} (payment moved to PAID/SIMULADO) or {@code "DECLINED"}
 * (payment stays PENDING). {@code motivo} is a human-readable reason present only on DECLINED (null on
 * APPROVED). Never carries card data (RN-RGPD-04).
 */
public record SimularPagoResponse(
        String resultado,
        String motivo
) {
    public static SimularPagoResponse approved() {
        return new SimularPagoResponse("APPROVED", null);
    }

    public static SimularPagoResponse declined(String motivo) {
        return new SimularPagoResponse("DECLINED", motivo);
    }
}
