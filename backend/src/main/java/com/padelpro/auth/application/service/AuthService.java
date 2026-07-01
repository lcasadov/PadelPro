package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.LoginCommand;
import com.padelpro.auth.application.dto.TokenPair;
import com.padelpro.auth.application.port.in.LoginUseCase;
import com.padelpro.auth.domain.exception.AccountNotActiveException;
import com.padelpro.auth.domain.exception.AuthenticationException;
import com.padelpro.auth.domain.model.AccountAccessPolicy;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.RefreshToken;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Application service implementing the {@link LoginUseCase} inbound port.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Anti-enumeration (RN-AUTH-06): unknown email and wrong password both return
 *       {@code AUTH_INVALID_CREDENTIALS}. BCrypt is always executed to prevent timing attacks.</li>
 *   <li>PENDING/INACTIVE accounts return {@code ACCOUNT_NOT_ACTIVE} (403, not 401).</li>
 *   <li>On success: JWT access token + refresh token generated, {@code last_login_at} updated.</li>
 *   <li>Audit log entries for LOGIN_SUCCESS and LOGIN_FAILURE (RN-RGPD-04: no password/token).</li>
 * </ul>
 *
 * <p>All repository dependencies are declared as domain port interfaces — no direct reference
 * to infrastructure adapters. Satisfies ArchUnit Rule 3 (Issue #79).
 *
 * <p><strong>Unit-test compatibility:</strong> {@link BCryptPasswordEncoder} and
 * {@link JwtService} are eagerly initialised as field defaults so that Mockito
 * {@code @InjectMocks} with only {@code @Mock UserRepositoryPort} / {@code @Mock UserRepository}
 * works without additional mock declarations. Spring replaces these defaults via setters.
 * Port fields for audit-log and refresh-token are {@code null}-safe so that unit tests
 * (which do not declare those mocks) do not NPE.
 */
@Service
public class AuthService implements LoginUseCase {

    private final UserRepositoryPort userRepository;

    // Eagerly initialised — Spring overrides via @Autowired setters in container context.
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private JwtService jwtService = new JwtService();

    // Optional ports — null in unit-test context (no Spring context).
    private AuditLogRepositoryPort auditLogRepository;
    private RefreshTokenRepositoryPort refreshTokenRepository;

    // Provisional-access policy (D8). Eagerly initialised with the default 48h grace window so
    // unit tests without a Spring context still apply the rule; Spring overrides via setter.
    private AccountAccessPolicy accessPolicy = new AccountAccessPolicy(Duration.ofHours(48));

    // Real BCrypt(12) hash of "__dummy_anti_timing__" — used when email is not found
    // to keep timing indistinguishable from a real password check (RN-AUTH-06).
    private static final String DUMMY_HASH =
            "$2a$12$zitW3oioFtmlnPsPFr7lduE2SFJ/sWS/rh1hu8MPKE0kuoV/L0T0O";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // -------------------------------------------------------------------------
    // Constructor — only UserRepositoryPort is mandatory
    // -------------------------------------------------------------------------

    public AuthService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Spring setter injection (replaces defaults when running in container)
    // -------------------------------------------------------------------------

    @Autowired
    public void setPasswordEncoder(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired
    public void setJwtService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Autowired(required = false)
    public void setAuditLogRepository(AuditLogRepositoryPort auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Autowired(required = false)
    public void setRefreshTokenRepository(RefreshTokenRepositoryPort refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Autowired(required = false)
    public void setAccessPolicy(AccountAccessPolicy accessPolicy) {
        if (accessPolicy != null) {
            this.accessPolicy = accessPolicy;
        }
    }

    // -------------------------------------------------------------------------
    // LoginUseCase
    // -------------------------------------------------------------------------

    @Override
    public TokenPair login(LoginCommand command) {
        Optional<User> userOpt = userRepository.findByEmail(command.email().toLowerCase());

        // Always run BCrypt to prevent timing-based user enumeration (RN-AUTH-06).
        // If the user does not exist, compare against a dummy hash.
        String hashToVerify = userOpt.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(command.password(), hashToVerify);

        if (userOpt.isEmpty() || !passwordMatches) {
            saveAuditLog("LOGIN_FAILURE", userOpt.orElse(null), null);
            throw new AuthenticationException();
        }

        User user = userOpt.get();

        // Provisional-access policy (D8): ACTIVE always; PENDING only within the 48h grace
        // window from registration; INACTIVE never. Otherwise → 403 ACCOUNT_NOT_ACTIVE.
        if (!accessPolicy.isAccessAllowed(user.getStatus(), user.getRegisteredAt(), OffsetDateTime.now())) {
            throw new AccountNotActiveException();
        }

        // Generate JWT access token
        String accessToken = jwtService.generateAccessToken(user);

        // Generate and persist refresh token (7-day expiry)
        String rawRefreshToken = generateRawRefreshToken();
        OffsetDateTime now = OffsetDateTime.now();

        if (refreshTokenRepository != null) {
            RefreshToken refreshToken = new RefreshToken(
                    user, sha256Hex(rawRefreshToken), now.plusSeconds(604800), null, now);
            refreshTokenRepository.save(refreshToken);
        }

        // Update last_login_at
        user.setLastLoginAt(now);
        userRepository.save(user);

        saveAuditLog("LOGIN_SUCCESS", user, null);

        return new TokenPair(accessToken, "Bearer", jwtService.getExpirySeconds(), rawRefreshToken);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void saveAuditLog(String action, User user, String ipAddress) {
        if (auditLogRepository != null) {
            auditLogRepository.save(
                    new AuditLog(action, user, ipAddress, null, OffsetDateTime.now()));
        }
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
