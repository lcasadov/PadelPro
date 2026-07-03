package com.padelpro.usuarios.application.dto;

/**
 * Command for admin-created user accounts (POST /api/admin/usuarios).
 *
 * <p>Admin-created users are set to {@code status=ACTIVE} immediately — no
 * approval step required (spec Requirement 2, scenario 1).
 *
 * <p><b>Password (change usuarios-alta-edicion-email, D2/D3):</b> the {@code password} field is
 * <b>ignored</b> by the service. The system generates a temporary password with
 * {@link com.padelpro.usuarios.application.service.TemporaryPasswordGenerator}, sets
 * {@code must_change_password = true}, and communicates it to the user via the welcome email. The
 * field is retained only for backward compatibility of the request shape.
 *
 * @param password ignored — the system generates the temporary password (D3)
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
