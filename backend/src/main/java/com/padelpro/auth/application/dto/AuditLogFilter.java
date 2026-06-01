package com.padelpro.auth.application.dto;

import java.time.OffsetDateTime;

/**
 * Immutable filter criteria for querying audit log entries.
 *
 * <p>All fields are nullable — a null value means "no filter on this dimension".
 */
public record AuditLogFilter(
        String action,
        Long userId,
        String entityType,
        OffsetDateTime from,
        OffsetDateTime to
) {}
