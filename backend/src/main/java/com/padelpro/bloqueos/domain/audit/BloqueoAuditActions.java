package com.padelpro.bloqueos.domain.audit;

/**
 * Audit action constants for the bloqueos-pista-eventos change. Written to {@code audit_log} with
 * only safe, non-PII context (date, hours, block id).
 */
public final class BloqueoAuditActions {

    public static final String BLOQUEO_CREATED = "BLOQUEO_CREATED";
    public static final String BLOQUEO_DELETED = "BLOQUEO_DELETED";

    private BloqueoAuditActions() {
    }
}
