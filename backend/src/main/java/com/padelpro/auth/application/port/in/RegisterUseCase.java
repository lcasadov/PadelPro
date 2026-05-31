package com.padelpro.auth.application.port.in;

import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.UserDto;

/**
 * Inbound port — register a new user in the platform.
 *
 * <p>Implemented by {@link com.padelpro.auth.application.service.RegistrationService}.
 * Logic (validation, BCrypt hashing, audit log) is added in Wave 3.
 */
public interface RegisterUseCase {

    /**
     * Register a new user.
     *
     * @param command the registration data (firstName, lastName, email, password)
     * @return a DTO of the newly created user
     */
    UserDto register(RegisterCommand command);
}
