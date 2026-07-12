package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.RefreshTokenRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import com.padelpro.reservas.domain.port.out.ParticipantCommandPort;
import com.padelpro.usuarios.application.dto.CreateUserAdminCommand;
import com.padelpro.usuarios.application.dto.PagedUsersResponse;
import com.padelpro.usuarios.application.dto.ResetPasswordResult;
import com.padelpro.usuarios.application.dto.UpdateUserAdminCommand;
import com.padelpro.usuarios.application.dto.UserAdminResponse;
import com.padelpro.usuarios.domain.audit.AuditActions;
import com.padelpro.usuarios.domain.exception.AdminSelfDeactivationException;
import com.padelpro.usuarios.domain.exception.EmailConflictException;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import com.padelpro.usuarios.domain.exception.UserNotPendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Application service for admin user-management operations.
 *
 * <p>All mutating operations produce an {@link AuditLog} entry.
 * IP address is null for admin operations (not originating from a user-facing HTTP request).
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    /** Neutral value written over personal name fields on anonymization (RN-RGPD-01). */
    static final String ANONYMIZED = "ANONIMIZADO";
    /** Domain of the synthetic, non-routable email assigned on anonymization (RN-RGPD-01). */
    static final String ANONYMIZED_EMAIL_DOMAIN = "@padelpro.local";

    private final UserRepositoryPort userRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final NotificationPort notificationPort;
    private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;
    private final OtpCodeRepositoryPort otpCodeRepositoryPort;
    private final ParticipantCommandPort participantCommandPort;

    public UserAdminService(UserRepositoryPort userRepositoryPort,
                            AuditLogRepositoryPort auditLogRepositoryPort,
                            BCryptPasswordEncoder passwordEncoder,
                            TemporaryPasswordGenerator temporaryPasswordGenerator,
                            NotificationPort notificationPort,
                            RefreshTokenRepositoryPort refreshTokenRepositoryPort,
                            OtpCodeRepositoryPort otpCodeRepositoryPort,
                            ParticipantCommandPort participantCommandPort) {
        this.userRepositoryPort   = userRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.passwordEncoder      = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.notificationPort     = notificationPort;
        this.refreshTokenRepositoryPort = refreshTokenRepositoryPort;
        this.otpCodeRepositoryPort = otpCodeRepositoryPort;
        this.participantCommandPort = participantCommandPort;
    }

    /**
     * Create a user directly as ACTIVE (no approval step required for admin-created accounts).
     *
     * @throws EmailConflictException if email is already in use
     * @throws IllegalArgumentException if login is already in use
     */
    @Transactional
    public UserAdminResponse createUser(CreateUserAdminCommand cmd) {
        if (userRepositoryPort.existsByEmail(cmd.email())) {
            throw new EmailConflictException();
        }
        if (userRepositoryPort.existsByLogin(cmd.login())) {
            throw new IllegalArgumentException("Login already exists: " + cmd.login());
        }

        UserRole role = (cmd.role() != null)
                ? UserRole.valueOf(cmd.role().toUpperCase())
                : UserRole.USER;

        // D2/D3: the system generates the temporary password (the admin does not type it, and any
        // password in the request is ignored). It is communicated to the user via the welcome email.
        String temporaryPassword = temporaryPasswordGenerator.generate();

        OffsetDateTime now = OffsetDateTime.now();
        User user = new User(
                cmd.login(),
                passwordEncoder.encode(temporaryPassword),
                cmd.firstName(),
                cmd.lastName(),
                cmd.email(),
                role,
                UserStatus.ACTIVE,
                now,
                now
        );
        user.setPhone(cmd.phone());
        user.setMustChangePassword(true);

        User saved = userRepositoryPort.save(user);

        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_CREATED_BY_ADMIN, saved, null,
                "login=" + cmd.login(), OffsetDateTime.now()));

        // D3 (direct-creation flow): welcome email WITH the temporary password. Best-effort/async;
        // a failure never breaks the creation (D4). RN-RGPD-04: the password is not logged here.
        sendWelcomeEmailSafely(
                WelcomeEmail.withPassword(saved.getEmail(), saved.getFirstName(), temporaryPassword),
                saved.getEmail());

        return toAdminResponse(saved);
    }

    /**
     * Approve a PENDING user, setting their status to ACTIVE.
     *
     * @throws UserNotFoundException  if no user with this id exists
     * @throws UserNotPendingException if the user is not in PENDING status
     */
    @Transactional
    public UserAdminResponse approveUser(Long targetId) {
        User user = requireUser(targetId);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserNotPendingException(targetId);
        }
        // D3: approval does NOT reset the password — the user keeps the one chosen at registration.
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepositoryPort.save(user);

        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_APPROVED, saved, null,
                "userId=" + targetId, OffsetDateTime.now()));

        // D3 (approval flow): welcome email WITHOUT password ("account approved, you can log in").
        // Best-effort/async; a failure never breaks the approval (D4).
        sendWelcomeEmailSafely(
                WelcomeEmail.accountApproved(saved.getEmail(), saved.getFirstName()),
                saved.getEmail());

        return toAdminResponse(saved);
    }

    /**
     * Return a paged list of users, optionally filtered by status.
     * Page size is capped at 100 regardless of the caller's request.
     */
    @Transactional(readOnly = true)
    public PagedUsersResponse listUsers(UserStatus status, Pageable pageable) {
        int cappedSize = Math.min(pageable.getPageSize(), 100);
        Pageable capped = PageRequest.of(pageable.getPageNumber(), cappedSize,
                pageable.getSort());

        Page<User> page = (status != null)
                ? userRepositoryPort.findByStatus(status, capped)
                : userRepositoryPort.findAll(capped);

        List<UserAdminResponse> content = page.getContent().stream()
                .map(UserAdminService::toAdminResponse)
                .toList();

        return new PagedUsersResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    /**
     * Return a single user by id.
     *
     * @throws UserNotFoundException if no user with this id exists
     */
    @Transactional(readOnly = true)
    public UserAdminResponse getUser(Long id) {
        return toAdminResponse(requireUser(id));
    }

    /**
     * Apply a partial update to any user account.
     * Audits a role change if the role field is present and different.
     *
     * @throws UserNotFoundException  if no user with this id exists
     * @throws EmailConflictException if the new email is already used by another user
     */
    @Transactional
    public UserAdminResponse updateUser(Long id, UpdateUserAdminCommand cmd) {
        User user = requireUser(id);

        if (cmd.email() != null && !cmd.email().equals(user.getEmail())) {
            if (userRepositoryPort.existsByEmailAndIdNot(cmd.email(), id)) {
                throw new EmailConflictException();
            }
            user.setEmail(cmd.email());
        }
        if (cmd.firstName() != null) user.setFirstName(cmd.firstName());
        if (cmd.lastName()  != null) user.setLastName(cmd.lastName());
        if (cmd.phone()     != null) user.setPhone(cmd.phone());

        boolean roleChanged = false;
        if (cmd.role() != null) {
            UserRole newRole = UserRole.valueOf(cmd.role().toUpperCase());
            if (newRole != user.getRole()) {
                user.setRole(newRole);
                roleChanged = true;
            }
        }
        if (cmd.status() != null) {
            user.setStatus(UserStatus.valueOf(cmd.status().toUpperCase()));
        }

        User saved = userRepositoryPort.save(user);

        if (roleChanged) {
            auditLogRepositoryPort.save(new AuditLog(
                    AuditActions.USER_ROLE_CHANGED, saved, null,
                    "userId=" + id + " newRole=" + cmd.role(), OffsetDateTime.now()));
        }

        return toAdminResponse(saved);
    }

    /**
     * RGPD right-to-be-forgotten (Art. 17) — irreversibly anonymize a user account in a single
     * atomic transaction (capability exportaciones-rgpd, RN-RGPD-01/05/06/07). Backs
     * {@code DELETE /api/admin/usuarios/{id}} (D1: the former soft-delete endpoint now anonymizes;
     * the HTTP contract is unchanged — the controller still returns 204).
     *
     * <p>In one transaction this method:
     * <ul>
     *   <li>overwrites the personal fields of {@code users} with neutral values and sets
     *       {@code status = INACTIVE} (RN-RGPD-01/06);</li>
     *   <li>revokes every {@code refresh_tokens} row of the user (RN-RGPD-07);</li>
     *   <li>invalidates every active {@code otp_codes} row of the user (RN-RGPD-07);</li>
     *   <li>anonymizes the user's {@code participants} rows (RN-RGPD-01);</li>
     *   <li>writes a {@code USER_ANONYMIZED} audit entry attributed to the executing admin (D3).</li>
     * </ul>
     *
     * <p>Financial/audit history ({@code reservations}, {@code payments}, existing {@code audit_log})
     * is intentionally NOT touched (RN-RGPD-02/05). Rows are never hard-deleted.
     *
     * <p>Idempotent (D4): re-running it on an already-anonymized account simply re-writes the same
     * neutral values and records another audit entry, without failing.
     *
     * <p>An admin cannot anonymize their own account (RN-AUTH-05). RN-RGPD-04: no personal data
     * (name, email, phone, telegram id) is ever logged — only technical ids.
     *
     * @param targetId the id of the user to anonymize
     * @param adminId  the id of the requesting admin
     * @throws AdminSelfDeactivationException if targetId == adminId
     * @throws UserNotFoundException          if the target user does not exist
     */
    @Transactional
    public void deactivateUser(Long targetId, Long adminId) {
        if (targetId.equals(adminId)) {
            throw new AdminSelfDeactivationException();
        }
        User user = requireUser(targetId);
        // The audit entry is attributed to the executing admin (D3): user_id = admin. Resolved
        // before mutating the target so the reference is independent of the anonymized user.
        User admin = userRepositoryPort.findById(adminId).orElse(null);

        // 1) Overwrite personal fields with neutral values + mark INACTIVE (RN-RGPD-01/06).
        user.setFirstName(ANONYMIZED);
        user.setLastName(ANONYMIZED);
        user.setEmail("anonimized-" + targetId + ANONYMIZED_EMAIL_DOMAIN);
        user.setPhone(null);
        user.setTelegramChatId(null);
        user.setTelegramLinkedAt(null);
        user.setStatus(UserStatus.INACTIVE);
        userRepositoryPort.save(user);

        // 2) Terminate all sessions and pending one-time passwords (RN-RGPD-07).
        refreshTokenRepositoryPort.revokeAllByUserId(targetId);
        otpCodeRepositoryPort.invalidateAllActiveByUserId(targetId);

        // 3) Anonymize the user's participation rows (RN-RGPD-01).
        participantCommandPort.anonymizeByUserId(targetId);

        // 4) Audit the anonymization: user_id = executing admin, entity = USER/{targetId} (D3).
        //    RN-RGPD-04: no personal data in the details — only technical ids.
        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_ANONYMIZED, admin, null,
                "anonymizedBy=" + adminId, OffsetDateTime.now(),
                "USER", String.valueOf(targetId)));

        // RN-RGPD-04: log technical ids only, never the anonymized personal data.
        log.info("User anonymized: targetId={} by adminId={}", targetId, adminId);
    }

    /**
     * Reset a user's password (D3/D4/D9): generate a temporary password, persist its BCrypt hash,
     * mark {@code must_change_password = true}, and return the temporary password in clear exactly
     * once so the admin can communicate it. The temporary password is never persisted in clear nor
     * written to the audit log (RN-RGPD-04).
     *
     * <p>An admin cannot reset their own account through this flow — protecting the ADMIN identity
     * (RN-AUTH-05), consistent with the self-deactivation guard.
     *
     * @param targetId the id of the user whose password is reset
     * @param adminId  the id of the requesting admin
     * @throws AdminSelfDeactivationException if {@code targetId == adminId}
     * @throws UserNotFoundException          if the target user does not exist
     */
    @Transactional
    public ResetPasswordResult resetPassword(Long targetId, Long adminId) {
        if (targetId.equals(adminId)) {
            throw new AdminSelfDeactivationException();
        }
        User user = requireUser(targetId);

        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        userRepositoryPort.save(user);

        // RN-RGPD-04: never include the temporary password in the audit details.
        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.PASSWORD_RESET_BY_ADMIN, user, null,
                "resetBy=" + adminId, OffsetDateTime.now()));

        return new ResetPasswordResult(user.getId(), temporaryPassword, true);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private User requireUser(Long id) {
        return userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Fire the welcome email without letting any failure break the activation/creation (D4).
     *
     * <p>The SMTP adapter is {@code @Async} and already swallows delivery errors, but this guard
     * makes the calling flow robust even if the port implementation changes or is invoked
     * synchronously (e.g. in tests). RN-RGPD-04: only the recipient is logged, never a password.
     */
    private void sendWelcomeEmailSafely(WelcomeEmail email, String recipientEmail) {
        try {
            notificationPort.sendWelcomeEmail(email);
        } catch (Exception ex) {
            log.warn("Welcome email dispatch failed for {} — activation not affected: {}",
                    recipientEmail, ex.getClass().getSimpleName());
        }
    }

    static UserAdminResponse toAdminResponse(User user) {
        return new UserAdminResponse(
                user.getId(),
                user.getLogin(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getTelegramChatId() != null,
                user.getRegisteredAt(),
                user.getUpdatedAt()
        );
    }
}
