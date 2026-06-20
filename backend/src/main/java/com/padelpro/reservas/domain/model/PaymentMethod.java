package com.padelpro.reservas.domain.model;

/**
 * Payment method (data-model §3.5, enum {@code payment_method}).
 *
 * <p>NULL until the payment is initiated. In the MVP the only method is {@link #CASH}
 * (registered manually by an ADMIN); {@link #REDSYS} arrives with Wave 4A.
 */
public enum PaymentMethod {
    REDSYS,
    CASH
}
