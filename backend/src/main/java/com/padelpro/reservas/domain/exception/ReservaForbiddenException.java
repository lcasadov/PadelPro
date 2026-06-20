package com.padelpro.reservas.domain.exception;

/**
 * Thrown when the caller is not allowed to read or act on a reservation (RN-AUTH-01/02).
 * Surfaces as HTTP 403 {@code FORBIDDEN}.
 *
 * <p>Used for the BOLA-prevention case too: a USER who is neither owner nor participant gets 403
 * (never 404) on {@code GET /api/reservas/{id}} so the existence of the resource is not revealed.
 */
public class ReservaForbiddenException extends RuntimeException {
    public ReservaForbiddenException(String message) {
        super(message);
    }
}
