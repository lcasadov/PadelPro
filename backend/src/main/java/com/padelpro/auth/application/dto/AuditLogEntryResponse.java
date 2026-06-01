package com.padelpro.auth.application.dto;

import java.time.OffsetDateTime;

/**
 * Read model returned by {@code AuditLogService.findAuditLogs}.
 *
 * <p>{@code userId} and {@code userEmail} are null when the audit entry was recorded
 * for an anonymous request (e.g. failed login with unknown email).
 */
public record AuditLogEntryResponse(
        Long id,
        String action,
        Long userId,
        String userEmail,
        String ipAddress,
        String details,
        String entityType,
        String entityId,
        String channel,
        OffsetDateTime createdAt
) {}
