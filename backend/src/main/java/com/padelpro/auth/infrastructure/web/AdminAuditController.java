package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.application.dto.PagedAuditLogResponse;
import com.padelpro.auth.application.service.AuditLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST controller exposing the audit log query endpoint to ADMIN users.
 *
 * <p>All endpoints require {@code ROLE_ADMIN} — enforced by {@code @PreAuthorize}
 * (requires {@code @EnableMethodSecurity} already active on {@link
 * com.padelpro.auth.infrastructure.config.SecurityConfig}).
 *
 * <p>Lives in {@code infrastructure.web} — satisfies ArchUnit Rule 4.
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * GET /api/admin/audit
     *
     * <p>Returns a paginated list of audit log entries. All query parameters are optional.
     *
     * @param action     filter by exact action value (e.g. "LOGIN_SUCCESS")
     * @param userId     filter by user id
     * @param entityType filter by entity type (e.g. "USER")
     * @param from       lower bound on createdAt (ISO-8601 with offset)
     * @param to         upper bound on createdAt (ISO-8601 with offset)
     * @param page       0-based page number (default 0)
     * @param size       page size (default 20, max 100)
     * @param sort       sort expression in the form "field,direction" (default "createdAt,desc")
     * @return 200 with paginated audit log entries
     */
    @GetMapping
    public ResponseEntity<PagedAuditLogResponse> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        AuditLogFilter filter = new AuditLogFilter(action, userId, entityType, from, to);
        Pageable pageable = buildPageable(page, size, sort);
        PagedAuditLogResponse response = auditLogService.findAuditLogs(filter, pageable);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Pageable buildPageable(int page, int size, String sortParam) {
        Sort sortObj;
        try {
            String[] parts = sortParam.split(",");
            String property = parts[0].trim();
            Sort.Direction direction = parts.length > 1
                    ? Sort.Direction.fromString(parts[1].trim())
                    : Sort.Direction.DESC;
            sortObj = Sort.by(direction, property);
        } catch (Exception e) {
            sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return PageRequest.of(page, size, sortObj);
    }
}
