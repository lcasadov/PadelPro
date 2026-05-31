package com.padelpro.usuarios.application.dto;

/**
 * Command for admin updates to any user account (PATCH /api/admin/usuarios/{id}).
 *
 * <p>All fields are nullable — only non-null values are applied.
 * {@code login} is intentionally absent: login is immutable post-creation.
 */
public record UpdateUserAdminCommand(
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        String status
) {}
