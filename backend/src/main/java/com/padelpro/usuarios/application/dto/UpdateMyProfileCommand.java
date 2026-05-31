package com.padelpro.usuarios.application.dto;

/**
 * Command for updating the authenticated user's own profile (PATCH /api/usuarios/me).
 *
 * <p>All fields are nullable — only non-null values are applied (partial update semantics).
 * {@code role} and {@code status} are intentionally absent: users cannot modify their own
 * role or status (spec Requirement 1, scenario 3).
 */
public record UpdateMyProfileCommand(
        String firstName,
        String lastName,
        String email,
        String phone
) {}
