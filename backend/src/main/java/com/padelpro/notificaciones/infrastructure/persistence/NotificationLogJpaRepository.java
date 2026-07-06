package com.padelpro.notificaciones.infrastructure.persistence;

import com.padelpro.notificaciones.domain.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationLog} (change
 * {@code notificaciones-eventos-email}, D2).
 */
@Repository
public interface NotificationLogJpaRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * FAILED entries still below the attempt cap, oldest first, for the retry job (Req 3).
     * Backoff pacing is applied in the application layer.
     */
    @Query("SELECT n FROM NotificationLog n "
            + "WHERE n.status = com.padelpro.notificaciones.domain.model.NotificationStatus.FAILED "
            + "AND n.attempts < :maxAttempts "
            + "ORDER BY n.createdAt ASC")
    List<NotificationLog> findRetriable(@Param("maxAttempts") int maxAttempts);
}
