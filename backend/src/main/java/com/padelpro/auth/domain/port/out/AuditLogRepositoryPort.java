package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.AuditLog;

/**
 * Outbound port — persistence operations on {@link AuditLog}.
 *
 * <p>Application services depend on this interface rather than the concrete
 * Spring Data repository, satisfying ArchUnit Rule 3 (Issue #79).
 */
public interface AuditLogRepositoryPort {

    /**
     * Persist a new audit log entry.
     *
     * @param auditLog the entry to save (must not include password or token data — RN-RGPD-04)
     * @return the saved entry with its generated id
     */
    AuditLog save(AuditLog auditLog);
}
