package com.padelpro.reservas.domain.exception;

/**
 * Thrown when a reservation cannot be created because its time slot overlaps an existing active
 * reservation (RN-RES-01). Surfaces as HTTP 409 {@code CONFLICT}.
 *
 * <p>Raised by translating the PostgreSQL {@code excl_res_no_overlap} gist exclusion violation
 * (design D1) — no application-level {@code SELECT FOR UPDATE} is used.
 */
public class SlotConflictException extends RuntimeException {
    public SlotConflictException(String message) {
        super(message);
    }
}
