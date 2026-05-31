package com.padelpro.usuarios.domain.exception;

/**
 * Thrown when an admin attempts to deactivate their own account (RN-AUTH-05).
 */
public class AdminSelfDeactivationException extends RuntimeException {

    public AdminSelfDeactivationException() {
        super("An admin cannot deactivate their own account");
    }
}
