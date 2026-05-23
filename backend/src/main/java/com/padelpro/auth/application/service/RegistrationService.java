package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.RegisterCommand;
import com.padelpro.auth.application.dto.UserDto;
import com.padelpro.auth.application.port.in.RegisterUseCase;
import com.padelpro.auth.domain.exception.EmailAlreadyExistsException;
import com.padelpro.auth.domain.exception.InvalidPasswordException;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service implementing the {@link RegisterUseCase} inbound port.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Required fields (firstName, lastName, email, password) must not be null/blank.</li>
 *   <li>Password policy (RN-AUTH-08): 8–128 chars, ≥1 uppercase, ≥1 digit.</li>
 *   <li>Email uniqueness: duplicate email → {@link EmailAlreadyExistsException}.</li>
 *   <li>Password hashed with BCrypt cost 12 before persistence.</li>
 *   <li>New users created with status {@code PENDING} and role {@code USER}.</li>
 *   <li>Audit log entry with action {@code USER_REGISTERED} (RN-RGPD-04: no PII in details).</li>
 * </ul>
 *
 * <p>All repository dependencies are declared as domain port interfaces — no direct reference
 * to infrastructure adapters. Satisfies ArchUnit Rule 3 (Issue #79).
 *
 * <p><strong>Unit-test compatibility:</strong> {@link BCryptPasswordEncoder} is eagerly
 * initialised as a field default so that Mockito {@code @InjectMocks} with only
 * {@code @Mock UserRepository} works without a {@code @Mock BCryptPasswordEncoder}.
 * {@link AuditLogRepositoryPort} is {@code null}-safe so unit tests that don't declare
 * it as a mock do not NPE.
 */
@Service
public class RegistrationService implements RegisterUseCase {

    private final UserRepositoryPort userRepository;

    // Eagerly initialised — Spring overrides via @Autowired setter in container context.
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    // Optional — null in unit-test context.
    private AuditLogRepositoryPort auditLogRepository;

    // -------------------------------------------------------------------------
    // Constructor — only UserRepositoryPort is mandatory
    // -------------------------------------------------------------------------

    public RegistrationService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Spring setter injection (replaces defaults when running in container)
    // -------------------------------------------------------------------------

    @Autowired
    public void setPasswordEncoder(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired(required = false)
    public void setAuditLogRepository(AuditLogRepositoryPort auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // -------------------------------------------------------------------------
    // RegisterUseCase
    // -------------------------------------------------------------------------

    @Override
    public UserDto register(RegisterCommand command) {
        // 1. Validate required fields
        validateRequired(command);

        // 2. Validate password policy (fail fast before DB calls)
        validatePassword(command.password());

        // 3. Check email uniqueness
        if (userRepository.existsByEmail(command.email().toLowerCase())) {
            throw new EmailAlreadyExistsException(command.email());
        }

        // 4. Hash password with BCrypt cost 12
        String passwordHash = passwordEncoder.encode(command.password());

        // 5. Build entity — status PENDING, role USER
        OffsetDateTime now = OffsetDateTime.now();
        User user = new User(
                command.email().toLowerCase(),  // login = email for bootstrap-mvp
                passwordHash,
                command.firstName(),
                command.lastName(),
                command.email().toLowerCase(),
                UserRole.USER,
                UserStatus.PENDING,
                now,
                now
        );

        // 6. Persist
        User saved = userRepository.save(user);

        // 7. Audit log — RN-RGPD-04: no password or token in details
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    "USER_REGISTERED", saved, null, null, now));
        }

        // 8. Return DTO
        return new UserDto(saved.getId(), saved.getEmail(), saved.getRole());
    }

    // -------------------------------------------------------------------------
    // Internal validation helpers
    // -------------------------------------------------------------------------

    private void validateRequired(RegisterCommand command) {
        if (command.firstName() == null || command.firstName().isBlank()) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELD: firstName");
        }
        if (command.lastName() == null || command.lastName().isBlank()) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELD: lastName");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELD: email");
        }
        if (command.password() == null) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELD: password");
        }
    }

    /**
     * Enforces password policy (RN-AUTH-08):
     * 8–128 characters, at least one uppercase letter, at least one digit.
     *
     * @throws InvalidPasswordException listing all violated rule codes
     */
    private void validatePassword(String password) {
        List<String> violations = new ArrayList<>();
        if (password.length() < 8)                              violations.add("MIN_LENGTH_8");
        if (password.length() > 128)                            violations.add("MAX_LENGTH_128");
        if (password.chars().noneMatch(Character::isUpperCase)) violations.add("REQUIRES_UPPERCASE");
        if (password.chars().noneMatch(Character::isDigit))     violations.add("REQUIRES_NUMBER");
        if (!violations.isEmpty()) throw new InvalidPasswordException(violations);
    }
}
