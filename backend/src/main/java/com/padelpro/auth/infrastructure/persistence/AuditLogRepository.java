package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AuditLog} entity.
 *
 * <p>Implements {@link AuditLogRepositoryPort} so application services depend on the
 * domain port rather than this concrete adapter (ArchUnit Rule 3, Issue #79).
 *
 * <p>{@link JpaSpecificationExecutor} is added to support dynamic filtering via
 * {@link AuditLogSpecification} without declaring individual query methods.
 */
@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>,
                JpaSpecificationExecutor<AuditLog>,
                AuditLogRepositoryPort {

    /**
     * Filtered paginated query — delegates to {@link JpaSpecificationExecutor#findAll}.
     */
    @Override
    default Page<AuditLog> findFiltered(AuditLogFilter filter, Pageable pageable) {
        return findAll(AuditLogSpecification.buildSpec(filter), pageable);
    }
}
