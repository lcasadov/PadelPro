package com.padelpro.auth.application.dto;

/**
 * Inbound command carrying the credentials for a login attempt.
 * Passed to {@link com.padelpro.auth.application.port.in.LoginUseCase}.
 */
public record LoginCommand(
        String email,
        String password
) {}
