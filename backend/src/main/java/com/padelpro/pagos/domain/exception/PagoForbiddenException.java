package com.padelpro.pagos.domain.exception;

/**
 * The caller is not the owner of the reservation and may not pay it (RN-AUTH-04). Surfaces as HTTP 403.
 */
public class PagoForbiddenException extends RuntimeException {
    public PagoForbiddenException(String message) {
        super(message);
    }
}
