package com.padelpro.auth.application.port.in;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.TokenPair;

/**
 * Inbound port — authenticate a user with email + password credentials.
 *
 * <p>Implemented by {@link com.padelpro.auth.application.service.AuthService}.
 * Logic (BCrypt verify, JWT generation, refresh token persistence, audit log) is added in Wave 3.
 */
public interface LoginUseCase {

    /**
     * Authenticate a user and return an access/refresh token pair.
     *
     * @param command the login credentials (email, password)
     * @return a {@link TokenPair} containing the JWT access token details
     */
    TokenPair login(LoginCommand command);
}
