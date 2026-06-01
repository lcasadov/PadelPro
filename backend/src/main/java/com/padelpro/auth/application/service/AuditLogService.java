package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.AuditLogEntryResponse;
import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.application.dto.PagedAuditLogResponse;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for querying audit log entries.
 *
 * <p>Depends only on {@link AuditLogRepositoryPort} (a domain port interface),
 * satisfying ArchUnit Rule 3 — no direct reference to infrastructure adapters.
 *
 * <p>Enforces a maximum page size of 100 to prevent unbounded result sets.
 */
@Service
public class AuditLogService {

    private final AuditLogRepositoryPort auditLogRepositoryPort;

    public AuditLogService(AuditLogRepositoryPort auditLogRepositoryPort) {
        this.auditLogRepositoryPort = auditLogRepositoryPort;
    }

    /**
     * Return a paginated, optionally-filtered list of audit log entries.
     *
     * @param filter   filter criteria (all fields nullable — null means no restriction)
     * @param pageable pagination and sort; page size must be ≤ 100
     * @return paged response DTO
     * @throws ValidationException if {@code pageable.getPageSize() > 100}
     */
    public PagedAuditLogResponse findAuditLogs(AuditLogFilter filter, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new ValidationException("size must be between 1 and 100");
        }

        Page<AuditLog> page = auditLogRepositoryPort.findFiltered(filter, pageable);

        List<AuditLogEntryResponse> content = page.getContent().stream()
                .map(this::toEntryResponse)
                .toList();

        return new PagedAuditLogResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // -------------------------------------------------------------------------
    // Private mapping
    // -------------------------------------------------------------------------

    private AuditLogEntryResponse toEntryResponse(AuditLog log) {
        User user = log.getUser();
        return new AuditLogEntryResponse(
                log.getId(),
                log.getAction(),
                user != null ? user.getId()    : null,
                user != null ? user.getEmail() : null,
                log.getIpAddress(),
                log.getDetails(),
                log.getEntityType(),
                log.getEntityId(),
                log.getChannel(),
                log.getCreatedAt()
        );
    }
}
