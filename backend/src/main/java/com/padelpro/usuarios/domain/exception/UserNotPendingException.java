package com.padelpro.usuarios.domain.exception;

/**
 * Thrown when an approval operation is attempted on a user whose status is not PENDING.
 */
public class UserNotPendingException extends RuntimeException {

    public UserNotPendingException(Long id) {
        super("User " + id + " is not in PENDING status");
    }
}
