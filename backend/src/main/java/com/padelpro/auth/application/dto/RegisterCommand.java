package com.padelpro.auth.application.dto;

/**
 * Inbound command carrying the data required to register a new user.
 * Validated at the controller layer before being passed to {@link com.padelpro.auth.application.port.in.RegisterUseCase}.
 */
public record RegisterCommand(
        String firstName,
        String lastName,
        String email,
        String password
) {}
