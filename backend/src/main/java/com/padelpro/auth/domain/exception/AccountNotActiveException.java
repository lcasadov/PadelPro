package com.padelpro.auth.domain.exception;

/**
 * Thrown when a login is attempted on an account that is not in ACTIVE status
 * (e.g. PENDING or INACTIVE).
 * Maps to HTTP 403 Forbidden with body {@code {error: "ACCOUNT_NOT_ACTIVE"}}.
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("ACCOUNT_NOT_ACTIVE");
    }
}
