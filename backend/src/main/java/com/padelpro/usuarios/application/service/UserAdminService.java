package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
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

    private final UserRepositoryPort userRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    public UserAdminService(UserRepositoryPort userRepositoryPort,
                            AuditLogRepositoryPort auditLogRepositoryPort,
                            BCryptPasswordEncoder passwordEncoder,
                            TemporaryPasswordGenerator temporaryPasswordGenerator) {
        this.userRepositoryPort   = userRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.passwordEncoder      = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
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

        OffsetDateTime now = OffsetDateTime.now();
        User user = new User(
                cmd.login(),
                passwordEncoder.encode(cmd.password()),
                cmd.firstName(),
                cmd.lastName(),
                cmd.email(),
                role,
                UserStatus.ACTIVE,
                now,
                now
        );
        user.setPhone(cmd.phone());

        User saved = userRepositoryPort.save(user);

        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_CREATED_BY_ADMIN, saved, null,
                "login=" + cmd.login(), OffsetDateTime.now()));

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
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepositoryPort.save(user);

        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_APPROVED, saved, null,
                "userId=" + targetId, OffsetDateTime.now()));

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
     * Deactivate a user (soft-delete via status=INACTIVE).
     * An admin cannot deactivate their own account (RN-AUTH-05).
     *
     * @param targetId the id of the user to deactivate
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
        user.setStatus(UserStatus.INACTIVE);
        User saved = userRepositoryPort.save(user);

        auditLogRepositoryPort.save(new AuditLog(
                AuditActions.USER_DEACTIVATED, saved, null,
                "deactivatedBy=" + adminId, OffsetDateTime.now()));
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
