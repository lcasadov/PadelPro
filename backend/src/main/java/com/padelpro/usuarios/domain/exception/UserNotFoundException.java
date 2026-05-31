package com.padelpro.usuarios.domain.exception;

/**
 * Thrown when a requested user does not exist in the system.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("User not found: " + id);
    }
}
