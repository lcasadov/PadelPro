package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.exception.AuthenticationException;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.PasswordPolicy;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Lets an authenticated user change their own password (D9).
 *
 * <p>Validates the current password, enforces the shared {@link PasswordPolicy} on the new one,
 * persists the new BCrypt hash and clears {@code must_change_password}. The passwords are never
 * logged (RN-RGPD-04): only a neutral audit action is recorded.
 */
@Service
public class ChangeMyPasswordService {

    private final UserRepositoryPort userRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public ChangeMyPasswordService(UserRepositoryPort userRepository,
                                   AuditLogRepositoryPort auditLogRepository,
                                   BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @throws UserNotFoundException          if the user does not exist
     * @throws AuthenticationException        if the current password is wrong
     * @throws com.padelpro.auth.domain.exception.InvalidPasswordException if the new password
     *                                        violates the policy
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException();
        }

        // Enforce policy before touching persistence.
        PasswordPolicy.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditLogRepository.save(new AuditLog(
                "PASSWORD_CHANGED", user, null, "self-service", OffsetDateTime.now()));
    }
}
