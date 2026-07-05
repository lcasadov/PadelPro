package com.padelpro.pagos.domain.exception;

/**
 * The payment operation is not applicable in the current state (e.g. the reservation is cancelled, the
 * payment is already collected, or Redsys is not configured). Surfaces as HTTP 422 carrying a
 * machine-readable {@code code}.
 */
public class PagoUnprocessableException extends RuntimeException {

    private final String code;

    public PagoUnprocessableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
