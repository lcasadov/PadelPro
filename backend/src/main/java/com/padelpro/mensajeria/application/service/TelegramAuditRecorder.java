package com.padelpro.mensajeria.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;

import java.time.OffsetDateTime;

/**
 * Writes Telegram messaging audit events to {@code audit_log} (mirrors {@code PagoAuditRecorder}).
 * {@code userId} is {@code null} for the anonymous webhook path (e.g. an invalid secret).
 */
public class TelegramAuditRecorder {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    public TelegramAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                 UserRepositoryPort userRepositoryPort) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    public void record(String action, Long userId, String details) {
        User user = (userId == null) ? null
                : userRepositoryPort.findById(userId).orElse(null);
        auditLogRepositoryPort.save(new AuditLog(action, user, null, details, OffsetDateTime.now()));
    }
}
