package com.padelpro.pagos.application.service;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;

import java.time.OffsetDateTime;

/**
 * Writes payment audit events to {@code audit_log} (pagos-redsys-online). Resolves the optional acting
 * user by id (null for the server-to-server webhook path). {@code details} must never contain card
 * data or the full {@code Ds_MerchantParameters} (RN-PAY-03).
 */
public class PagoAuditRecorder {

    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    public PagoAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                             UserRepositoryPort userRepositoryPort) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    /**
     * @param action  audit action constant (see {@code PagoAuditActions})
     * @param userId  the acting user id, or {@code null} (webhook has no authenticated principal)
     * @param details safe, non-PII, non-card context (e.g. {@code "orderId=..., status=..."})
     */
    public void record(String action, Long userId, String details) {
        User user = (userId == null) ? null
                : userRepositoryPort.findById(userId).orElse(null);
        auditLogRepositoryPort.save(new AuditLog(action, user, null, details, OffsetDateTime.now()));
    }
}
