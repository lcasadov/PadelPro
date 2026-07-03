package com.padelpro.usuarios.application.dto;

import jakarta.validation.constraints.Email;

/**
 * Command for admin updates to any user account (PATCH /api/admin/usuarios/{id}).
 *
 * <p>All fields are nullable — only non-null values are applied.
 * {@code login} is intentionally absent: login is immutable post-creation.
 *
 * <p>{@code email} is optional (partial update), but when present it MUST be a
 * well-formed address — {@code @Email} treats {@code null} as valid, so
 * phone-only or name-only patches are unaffected.
 */
public record UpdateUserAdminCommand(
        String firstName,
        String lastName,
        @Email(message = "email") String email,
        String phone,
        String role,
        String status
) {}
