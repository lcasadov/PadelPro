package com.padelpro.auth.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Provisions real, persistent users (one ADMIN, one USER) directly in the database so the team can
 * log in and run tests on the deployed stack without going through the PENDING self-registration +
 * admin-activation flow.
 *
 * <p>Unlike {@link RegistrationService} (which leaves new accounts {@link UserStatus#PENDING}), these
 * users are created {@link UserStatus#ACTIVE} and can log in immediately. Idempotent: each user is
 * created only if no account with that email exists yet, so repeated deploys never duplicate rows.
 * Passwords are BCrypt-hashed at runtime; no credential is ever stored raw or logged (RN-RGPD-04).
 */
@Service
public class SeedUsersService {

    private final UserRepositoryPort userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public SeedUsersService(UserRepositoryPort userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create the user if, and only if, the credentials are non-blank and no account with the given
     * email exists yet.
     *
     * @return {@code true} if the user was created, {@code false} otherwise (already present or
     * credentials absent).
     */
    @Transactional
    public boolean seedIfAbsent(String email, String rawPassword, String firstName, String lastName, UserRole role) {
        if (isBlank(email) || isBlank(rawPassword)) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        User user = new User(
                normalizedEmail,                       // login = email (bootstrap convention)
                passwordEncoder.encode(rawPassword),
                firstName,
                lastName,
                normalizedEmail,
                role,
                UserStatus.ACTIVE,
                now,
                now
        );
        userRepository.save(user);
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
