package com.padelpro.usuarios.infrastructure.web;

import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.usuarios.application.dto.CreateUserAdminCommand;
import com.padelpro.usuarios.application.dto.PagedUsersResponse;
import com.padelpro.usuarios.application.dto.ResetPasswordResult;
import com.padelpro.usuarios.application.dto.UpdateUserAdminCommand;
import com.padelpro.usuarios.application.dto.UserAdminResponse;
import com.padelpro.usuarios.application.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for ADMIN user-management operations.
 *
 * <p>All endpoints require {@code ROLE_ADMIN} — enforced at the method level via
 * {@code @PreAuthorize} (requires {@code @EnableMethodSecurity} on {@link
 * com.padelpro.auth.infrastructure.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsuariosController {

    private final UserAdminService userAdminService;

    public AdminUsuariosController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * GET /api/admin/usuarios?status=&page=&size=
     * Lists users, optionally filtered by status. Page size is capped at 100.
     */
    @GetMapping
    public ResponseEntity<PagedUsersResponse> listUsers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UserStatus statusFilter = (status != null && !status.isBlank())
                ? UserStatus.valueOf(status.toUpperCase())
                : null;
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userAdminService.listUsers(statusFilter, pageable));
    }

    /**
     * POST /api/admin/usuarios
     * Creates a new user directly with status=ACTIVE.
     */
    @PostMapping
    public ResponseEntity<UserAdminResponse> createUser(
            @Valid @RequestBody CreateUserAdminCommand command) {
        UserAdminResponse created = userAdminService.createUser(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/admin/usuarios/{id}
     * Returns the full profile of a specific user.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserAdminResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getUser(id));
    }

    /**
     * PATCH /api/admin/usuarios/{id}
     * Partially updates any user (including role and status).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserAdminResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserAdminCommand command) {
        return ResponseEntity.ok(userAdminService.updateUser(id, command));
    }

    /**
     * PATCH /api/admin/usuarios/{id}/aprobar
     * Approves a PENDING user, setting their status to ACTIVE.
     */
    @PatchMapping("/{id}/aprobar")
    public ResponseEntity<UserAdminResponse> approveUser(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.approveUser(id));
    }

    /**
     * DELETE /api/admin/usuarios/{id}
     * RGPD right-to-be-forgotten (Art. 17): irreversibly anonymizes the user's personal data,
     * sets status=INACTIVE, revokes tokens and invalidates OTP codes in one atomic transaction
     * (capability exportaciones-rgpd, RN-RGPD-01). Financial/audit history is preserved.
     * The response is 204 No Content (contract unchanged). An admin cannot anonymize their own
     * account (RN-AUTH-05).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        Long adminId = resolveAdminId();
        userAdminService.deactivateUser(id, adminId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/admin/usuarios/{id}/reset-password
     * Generates a temporary password for the target user, persists it as BCrypt, marks the
     * account with {@code must_change_password=true}, and returns the temporary password in clear
     * exactly once (D3/D4). An admin cannot reset their own account (RN-AUTH-05).
     */
    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResult> resetPassword(@PathVariable Long id) {
        Long adminId = resolveAdminId();
        return ResponseEntity.ok(userAdminService.resetPassword(id, adminId));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Long resolveAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
