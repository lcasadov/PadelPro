package com.padelpro.otp.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;

import java.time.OffsetDateTime;

/**
 * Writes auth-otp-telegram audit events to {@code audit_log} (mirrors {@code PagoAuditRecorder}).
 * Resolves the optional acting user by id ({@code null} for the anonymous webhook path).
 *
 * <p>{@code details} must never contain the clear OTP code (RN-RGPD-04) — only ids, types and
 * outcome codes.
 */
public class OtpAuditRecorder {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    public OtpAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
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
