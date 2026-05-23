package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.UserDto;
import com.padelpro.auth.application.port.in.RegisterUseCase;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Application service implementing the {@link RegisterUseCase} inbound port.
 *
 * <p><strong>Wave 2 skeleton</strong> — no business logic yet.
 * All registration logic (BCrypt hashing, duplicate-email check, audit log,
 * email + WhatsApp notification) will be implemented in Wave 3.
 */
@Service
public class RegistrationService implements RegisterUseCase {

    private final UserRepository userRepository;

    public RegistrationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException until Wave 3 implementation
     */
    @Override
    public UserDto register(RegisterCommand command) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
