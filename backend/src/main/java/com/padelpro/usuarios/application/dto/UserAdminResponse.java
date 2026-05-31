package com.padelpro.usuarios.application.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO returned by admin endpoints (full view of a user).
 *
 * <p>Includes {@code registeredAt} and {@code updatedAt} fields not present in
 * the self-profile view, giving the admin a complete picture of account lifecycle.
 */
public record UserAdminResponse(
        Long id,
        String login,
        String firstName,
        String lastName,
        String email,
        String phone,
        String status,
        String role,
        boolean telegramLinked,
        OffsetDateTime registeredAt,
        OffsetDateTime updatedAt
) {}
