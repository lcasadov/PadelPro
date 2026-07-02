package com.padelpro.usuarios.infrastructure.web;

import com.padelpro.usuarios.application.dto.ChangeMyPasswordRequest;
import com.padelpro.usuarios.application.dto.UpdateMyProfileCommand;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.application.service.ChangeMyPasswordService;
import com.padelpro.usuarios.application.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the authenticated user's own profile.
 *
 * <p>GET  /api/usuarios/me  — read own profile.<br>
 * PATCH /api/usuarios/me  — partial update of own profile (firstName, lastName, email, phone).
 *
 * <p>The JWT subject is the user id (Long as String), placed there by
 * {@link com.padelpro.auth.application.service.JwtService} and validated by
 * {@link com.padelpro.auth.infrastructure.web.filter.JwtAuthFilter}.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuariosMeController {

    private final UserProfileService userProfileService;
    private final ChangeMyPasswordService changeMyPasswordService;

    public UsuariosMeController(UserProfileService userProfileService,
                                ChangeMyPasswordService changeMyPasswordService) {
        this.userProfileService = userProfileService;
        this.changeMyPasswordService = changeMyPasswordService;
    }

    /**
     * GET /api/usuarios/me
     * Returns the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        Long userId = resolveUserId();
        return ResponseEntity.ok(userProfileService.getMyProfile(userId));
    }

    /**
     * PATCH /api/usuarios/me
     * Partially updates the profile of the currently authenticated user.
     * Fields {@code role} and {@code status} are intentionally absent from
     * {@link UpdateMyProfileCommand} — they are silently ignored if sent in the body.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestBody UpdateMyProfileCommand command) {
        Long userId = resolveUserId();
        return ResponseEntity.ok(userProfileService.updateMyProfile(userId, command));
    }

    /**
     * POST /api/usuarios/me/password
     * Changes the authenticated user's own password (D9): validates the current password and the
     * new-password policy, persists the new BCrypt hash and clears {@code must_change_password}.
     * Returns 204 on success.
     */
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@RequestBody ChangeMyPasswordRequest request) {
        Long userId = resolveUserId();
        changeMyPasswordService.changePassword(
                userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
