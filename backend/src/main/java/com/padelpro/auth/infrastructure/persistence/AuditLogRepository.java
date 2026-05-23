package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AuditLog} entity.
 * Query methods will be added in future iterations as the admin panel is built.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // No custom methods in bootstrap-mvp — only inherited CRUD
}
