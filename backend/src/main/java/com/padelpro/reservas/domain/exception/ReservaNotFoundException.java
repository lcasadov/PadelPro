package com.padelpro.reservas.domain.exception;

import java.util.UUID;

/**
 * Thrown when a reservation does not exist. Used by ADMIN paths (404) and by the cancel path where
 * the caller is the owner-or-admin scope. For non-owner USER detail access, prefer
 * {@link ReservaForbiddenException} (403, BOLA prevention) instead of leaking existence.
 */
public class ReservaNotFoundException extends RuntimeException {
    public ReservaNotFoundException(UUID id) {
        super("Reserva no encontrada: " + id);
    }
}
