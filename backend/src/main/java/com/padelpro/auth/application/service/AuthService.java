package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.application.port.in.LoginUseCase;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Application service implementing the {@link LoginUseCase} inbound port.
 *
 * <p><strong>Wave 2 skeleton</strong> — no business logic yet.
 * All login logic (BCrypt verification, JWT generation, refresh token persistence,
 * rate-limit enforcement, audit log) will be implemented in Wave 3.
 */
@Service
public class AuthService implements LoginUseCase {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    @Override
    public TokenPair login(LoginCommand command) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
