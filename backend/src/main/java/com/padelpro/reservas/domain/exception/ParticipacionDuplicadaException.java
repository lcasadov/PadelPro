package com.padelpro.reservas.domain.exception;

/**
 * Thrown when a user tries to join a reservation they already participate in (RN-AUTH-03). Surfaces
 * as HTTP 409 {@code CONFLICT} (error code {@code CONFLICT}); no duplicate participant row is created.
 */
public class ParticipacionDuplicadaException extends RuntimeException {
    public ParticipacionDuplicadaException(String message) {
        super(message);
    }
}
