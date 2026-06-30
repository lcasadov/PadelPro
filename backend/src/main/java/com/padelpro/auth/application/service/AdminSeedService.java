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
 * Bootstrap of the first administrator (D1, RN-AUTH-07).
 *
 * <p>Idempotent: an ADMIN is created only when none exists yet and both credentials are
 * provided. The password is hashed with BCrypt at runtime — no credential is ever stored in
 * the repository or in logs. If the credentials are missing/blank, nothing happens and the
 * application boots normally.
 */
@Service
public class AdminSeedService {

    private final UserRepositoryPort userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminSeedService(UserRepositoryPort userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create an ACTIVE ADMIN with a BCrypt-hashed password if, and only if, no ADMIN exists yet
     * and both {@code email} and {@code rawPassword} are non-blank.
     *
     * @return {@code true} if an admin was created, {@code false} otherwise (already present or
     * credentials absent).
     */
    @Transactional
    public boolean seedIfAbsent(String email, String rawPassword) {
        if (isBlank(email) || isBlank(rawPassword)) {
            return false;
        }
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        OffsetDateTime now = OffsetDateTime.now();
        User admin = new User(
                normalizedEmail,                       // login = email (bootstrap convention)
                passwordEncoder.encode(rawPassword),
                "Admin",
                "PadelPro",
                normalizedEmail,
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                now,
                now
        );
        userRepository.save(admin);
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
