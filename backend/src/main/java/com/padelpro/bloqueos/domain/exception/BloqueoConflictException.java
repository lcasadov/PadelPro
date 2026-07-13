package com.padelpro.bloqueos.domain.exception;

import java.util.List;

/**
 * At least one requested slot overlaps an active reservation (change bloqueos-pista-eventos, D3).
 * The block operation is all-or-nothing: no row is inserted. Surfaces as HTTP 409, carrying the list
 * of conflicting slots (as {@code HH:mm} strings) in the error {@code details}.
 */
public class BloqueoConflictException extends RuntimeException {

    private final List<String> conflictingSlots;

    public BloqueoConflictException(String message, List<String> conflictingSlots) {
        super(message);
        this.conflictingSlots = List.copyOf(conflictingSlots);
    }

    /** Conflicting slot-start hours as {@code HH:mm} strings (e.g. {@code ["19:00"]}). */
    public List<String> getConflictingSlots() {
        return conflictingSlots;
    }
}
