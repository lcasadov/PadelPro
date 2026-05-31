package com.padelpro.usuarios.application.dto;

/**
 * Command for admin-created user accounts (POST /api/admin/usuarios).
 *
 * <p>Admin-created users are set to {@code status=ACTIVE} immediately — no
 * approval step required (spec Requirement 2, scenario 1).
 * Password policy RN-AUTH-08 is enforced in the service layer.
 *
 * @param role optional — defaults to USER if null
 */
public record CreateUserAdminCommand(
        String login,
        String firstName,
        String lastName,
        String email,
        String password,
        String phone,
        String role
) {}
