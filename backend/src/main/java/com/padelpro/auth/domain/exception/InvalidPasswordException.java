package com.padelpro.auth.domain.exception;

import java.util.List;

/**
 * Thrown when a password fails the platform's policy rules.
 *
 * <p>The {@code violations} list carries one or more of:
 * {@code MIN_LENGTH_8}, {@code MAX_LENGTH_128}, {@code REQUIRES_UPPERCASE}, {@code REQUIRES_NUMBER}.
 * The exception message is the comma-joined list of violation codes so that tests can
 * assert via {@code containsIgnoringCase}.
 */
public class InvalidPasswordException extends RuntimeException {

    private final List<String> violations;

    public InvalidPasswordException(List<String> violations) {
        super(String.join(", ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
