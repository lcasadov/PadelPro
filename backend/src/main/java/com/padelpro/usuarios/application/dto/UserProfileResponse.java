package com.padelpro.usuarios.application.dto;

/**
 * Response DTO for the authenticated user's own profile (GET /api/usuarios/me).
 *
 * <p>{@code telegramLinked} is a computed boolean: {@code telegramChatId != null}.
 * Password hash and telegram_chat_id are never included in responses (RN-RGPD-03).
 */
public record UserProfileResponse(
        Long id,
        String login,
        String firstName,
        String lastName,
        String email,
        String phone,
        String status,
        String role,
        boolean telegramLinked
) {}
