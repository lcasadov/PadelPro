package com.padelpro.reservas.domain.exception;

/**
 * Thrown when an operation is not permitted in the reservation's current state: an invalid state
 * transition (D6 unidirectional machine) or a cancellation outside the allowed deadline without the
 * admin bypass (RN-RES-04). Surfaces as HTTP 422 {@code UNPROCESSABLE_ENTITY}.
 */
public class InvalidReservaStateException extends RuntimeException {

    private final String code;

    public InvalidReservaStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
