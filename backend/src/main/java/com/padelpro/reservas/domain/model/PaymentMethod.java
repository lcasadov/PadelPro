package com.padelpro.reservas.domain.model;

/**
 * Payment method (data-model §3.5, enum {@code payment_method}).
 *
 * <p>NULL until the payment is initiated. In the MVP the only method is {@link #CASH}
 * (registered manually by an ADMIN); {@link #REDSYS} arrives with Wave 4A.
 *
 * <p>{@link #SIMULADO} marks a payment confirmed by the provisional online simulator
 * ({@code POST /api/pagos/simular}, change pagos-simulador-gestion, D2) — kept distinct from the real
 * {@link #REDSYS} gateway so simulated collections are never mistaken for real ones. Added to the DB
 * enum by Flyway V18.
 */
public enum PaymentMethod {
    REDSYS,
    CASH,
    SIMULADO
}
