package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AuditLog} entity.
 *
 * <p>Implements {@link AuditLogRepositoryPort} so application services depend on the
 * domain port rather than this concrete adapter (ArchUnit Rule 3, Issue #79).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, AuditLogRepositoryPort {

    /**
     * Resolves the ambiguity between {@link AuditLogRepositoryPort#save(AuditLog)} and
     * the generic {@code <S>save(S)} from {@link JpaRepository}.
     */
    @Override
    @SuppressWarnings("unchecked")
    default AuditLog save(AuditLog auditLog) {
        throw new UnsupportedOperationException("Spring Data proxy must override this method");
    }
}
