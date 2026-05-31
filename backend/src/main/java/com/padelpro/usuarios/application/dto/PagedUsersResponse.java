package com.padelpro.usuarios.application.dto;

import java.util.List;

/**
 * Paged response for admin user listings (GET /api/admin/usuarios).
 */
public record PagedUsersResponse(
        List<UserAdminResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
