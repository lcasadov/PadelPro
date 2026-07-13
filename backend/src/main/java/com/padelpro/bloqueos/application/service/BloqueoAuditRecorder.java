package com.padelpro.bloqueos.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;

import java.time.OffsetDateTime;

/**
 * Writes block audit events to {@code audit_log} (change bloqueos-pista-eventos, D6). Resolves the
 * acting admin by id. {@code details} carries only date/hours/id — no PII.
 */
public class BloqueoAuditRecorder {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    public BloqueoAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                UserRepositoryPort userRepositoryPort) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    /**
     * @param action  audit action constant (see {@code BloqueoAuditActions})
     * @param userId  the acting admin id, or {@code null} when unknown
     * @param details safe, non-PII context (e.g. {@code "fecha=2026-08-01, horas=[18:00, 19:00]"})
     */
    public void record(String action, Long userId, String details) {
        User user = (userId == null) ? null
                : userRepositoryPort.findById(userId).orElse(null);
        auditLogRepositoryPort.save(new AuditLog(action, user, null, details, OffsetDateTime.now()));
    }
}
