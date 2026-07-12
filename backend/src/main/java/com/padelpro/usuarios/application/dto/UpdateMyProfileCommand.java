package com.padelpro.usuarios.application.dto;

/**
 * Command for updating the authenticated user's own profile (PATCH /api/usuarios/me).
 *
 * <p>All fields are nullable — only non-null values are applied (partial update semantics).
 * {@code role} and {@code status} are intentionally absent: users cannot modify their own
 * role or status (spec Requirement 1, scenario 3).
 *
 * <p>{@code telegramAction} (auth-otp-telegram) drives the two-step Telegram linking: {@code "LINK"}
 * starts the linking flow (generates a {@code TELEGRAM_LINK} OTP and returns instructions) and
 * {@code "UNLINK"} clears the link and revokes active OTPs. When present it is handled instead of the
 * plain profile-field update.
 */
public record UpdateMyProfileCommand(
        String firstName,
        String lastName,
        String email,
        String phone,
        String telegramAction
) {
    /** Backward-compatible constructor for plain profile updates (no Telegram action). */
    public UpdateMyProfileCommand(String firstName, String lastName, String email, String phone) {
        this(firstName, lastName, email, phone, null);
    }
}
