package com.padelpro.auth.application.dto;

import java.util.List;

/**
 * Paginated wrapper for {@link AuditLogEntryResponse} results.
 */
public record PagedAuditLogResponse(
        List<AuditLogEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
