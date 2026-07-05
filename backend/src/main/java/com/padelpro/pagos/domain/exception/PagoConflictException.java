package com.padelpro.pagos.domain.exception;

/**
 * A payment is already in progress or completed for this reservation (pagos-redsys-online, D5).
 * Surfaces as HTTP 409.
 */
public class PagoConflictException extends RuntimeException {
    public PagoConflictException(String message) {
        super(message);
    }
}
