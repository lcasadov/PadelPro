package com.padelpro.usuarios.application.dto;

/**
 * Response DTO for the authenticated user search (GET /api/usuarios/buscar).
 *
 * <p>Deliberately minimal (D5): only {@code id} and a display {@code nombre}. It never carries
 * email, password hash, role, status or any other field, so the endpoint cannot be used to dump
 * the member directory or leak PII (RN-RGPD). The email — used only to filter matches — is never
 * projected here.
 */
public record UserSearchResult(
        Long id,
        String nombre
) {}
