package com.padelpro.reservas.domain.model;

/**
 * Payment gateway (data-model §3.5, enum {@code payment_gateway}).
 *
 * <p>Distinct from {@code com.padelpro.auth.domain.model.SystemConfig.PaymentGateway} which only
 * models the gateways the club can activate (CASH/REDSYS). This enum mirrors the DB type used by
 * the {@code payments.gateway} column; in the MVP (CASH) it stays NULL.
 */
public enum PaymentGateway {
    REDSYS,
    STRIPE,
    PAYPAL
}
