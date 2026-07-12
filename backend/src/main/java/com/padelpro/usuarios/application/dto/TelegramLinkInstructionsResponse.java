package com.padelpro.usuarios.application.dto;

import java.time.OffsetDateTime;

/**
 * Response returned by {@code PATCH /api/usuarios/me} with {@code telegramAction=LINK}
 * (auth-otp-telegram, spec Requirement 1).
 *
 * <p>Carries the human-readable instruction to complete linking via the bot and the OTP expiry so
 * the UI can show a countdown (mockup 15). The clear code is never written to logs (RN-RGPD-04).
 *
 * @param instructions text to display, e.g. "Envía /vincular 123456 al bot @PadelProBot"
 * @param otpCode      the 6-digit code the user must send to the bot to complete linking
 * @param expiresAt    when the code expires (TTL 10 min, RN-AUTH-07)
 */
public record TelegramLinkInstructionsResponse(
        String instructions,
        String otpCode,
        OffsetDateTime expiresAt
) {
}
