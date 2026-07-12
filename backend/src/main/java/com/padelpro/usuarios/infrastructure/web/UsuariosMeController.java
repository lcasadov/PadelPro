package com.padelpro.usuarios.infrastructure.web;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.usuarios.application.dto.ChangeMyPasswordRequest;
import com.padelpro.usuarios.application.dto.UpdateMyProfileCommand;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.application.service.ChangeMyPasswordService;
import com.padelpro.usuarios.application.service.TelegramLinkService;
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
    private final TelegramLinkService telegramLinkService;

    public UsuariosMeController(UserProfileService userProfileService,
                                ChangeMyPasswordService changeMyPasswordService,
                                TelegramLinkService telegramLinkService) {
        this.userProfileService = userProfileService;
        this.changeMyPasswordService = changeMyPasswordService;
        this.telegramLinkService = telegramLinkService;
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
     *
     * <p>When {@code telegramAction} is present (auth-otp-telegram) the request is handled as a
     * Telegram link/unlink action instead of a plain profile update: {@code LINK} returns
     * {@link com.padelpro.usuarios.application.dto.TelegramLinkInstructionsResponse}; {@code UNLINK}
     * returns the updated {@link UserProfileResponse}.
     */
    @PatchMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @RequestBody UpdateMyProfileCommand command) {
        Long userId = resolveUserId();
        if (command.telegramAction() != null && !command.telegramAction().isBlank()) {
            String action = command.telegramAction().trim().toUpperCase();
            return switch (action) {
                case "LINK" -> ResponseEntity.ok(telegramLinkService.initiateLink(userId));
                case "UNLINK" -> ResponseEntity.ok(telegramLinkService.unlink(userId));
                default -> throw new ValidationException("telegramAction must be LINK or UNLINK");
            };
        }
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
