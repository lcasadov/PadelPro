package com.padelpro.auth.application.dto;

import com.padelpro.auth.domain.model.UserRole;

/**
 * Read-only projection of a {@link com.padelpro.auth.domain.model.User} entity.
 * Exposed over the REST API — no sensitive fields (e.g. password hash) are included.
 */
public record UserDto(
        Long id,
        String email,
        UserRole role
) {}
