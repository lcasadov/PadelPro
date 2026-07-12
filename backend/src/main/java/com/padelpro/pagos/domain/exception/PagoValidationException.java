package com.padelpro.pagos.domain.exception;

/**
 * The payment request carries a malformed field (card number, expiry or CVC) in the online simulator
 * (change pagos-simulador-gestion, D2). Surfaces as HTTP 400 with code {@code VALIDATION_ERROR}.
 *
 * <p>The message must never echo card data or the CVC (RN-RGPD-04) — it only names the offending field
 * generically.
 */
public class PagoValidationException extends RuntimeException {
    public PagoValidationException(String message) {
        super(message);
    }
}
