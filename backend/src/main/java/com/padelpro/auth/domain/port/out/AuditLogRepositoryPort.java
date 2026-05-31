package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.domain.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * Return a page of audit log entries matching the given filter.
     *
     * <p>All filter fields are optional — a null field means "no restriction on that dimension".
     *
     * @param filter   filter criteria (nullable fields)
     * @param pageable pagination and sorting
     * @return page of matching entries
     */
    Page<AuditLog> findFiltered(AuditLogFilter filter, Pageable pageable);
}
