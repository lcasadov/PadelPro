package com.padelpro.usuarios.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /api/usuarios/me/password} — the user's own password change (D9).
 *
 * <p>Never logged (RN-RGPD-04).
 *
 * @param currentPassword the user's current password (verified before the change)
 * @param newPassword     the new password (must satisfy the password policy)
 */
public record ChangeMyPasswordRequest(
        @JsonProperty("current_password") String currentPassword,
        @JsonProperty("new_password")     String newPassword
) {
}
