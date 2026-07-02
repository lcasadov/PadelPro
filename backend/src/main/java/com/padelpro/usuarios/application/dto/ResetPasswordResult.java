package com.padelpro.usuarios.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of an admin password reset (D3/D4).
 *
 * <p>Carries the generated temporary password in clear — returned to the admin exactly once so
 * they can communicate it to the user. It is never persisted in clear nor logged (RN-RGPD-04).
 *
 * @param userId               the id of the user whose password was reset
 * @param temporaryPassword    the one-time temporary password in clear
 * @param mustChangePassword   always {@code true} — the user must change it on next login
 */
public record ResetPasswordResult(
        @JsonProperty("user_id")              Long userId,
        @JsonProperty("temporary_password")   String temporaryPassword,
        @JsonProperty("must_change_password") boolean mustChangePassword
) {
}
