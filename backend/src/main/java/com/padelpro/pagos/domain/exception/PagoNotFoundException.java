package com.padelpro.pagos.domain.exception;

/**
 * The reservation or its payment does not exist (pagos-redsys-online). Surfaces as HTTP 404.
 */
public class PagoNotFoundException extends RuntimeException {
    public PagoNotFoundException(String message) {
        super(message);
    }
}
