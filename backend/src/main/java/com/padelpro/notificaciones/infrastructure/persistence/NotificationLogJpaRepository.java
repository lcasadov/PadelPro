package com.padelpro.notificaciones.infrastructure.persistence;

import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.model.NotificationType;
import org.springframework.data.domain.Pageable;
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
     * Backoff pacing is applied in the application layer. The {@link Pageable} caps how many entries a
     * single retry cycle loads so a large backlog can never exhaust memory in one shot.
     *
     * <p>Filtered to {@code type} = {@code EMAIL}: the retry job re-sends via SMTP only, so Telegram
     * ({@code TELEGRAM_DIRECT}/{@code TELEGRAM_GROUP}) FAILED entries — which are audit-only — are never
     * picked up and wrongly re-sent as emails (notificaciones-telegram, D3).
     */
    @Query("SELECT n FROM NotificationLog n "
            + "WHERE n.status = :status "
            + "AND n.type = :type "
            + "AND n.attempts < :maxAttempts "
            + "ORDER BY n.createdAt ASC")
    List<NotificationLog> findRetriable(@Param("status") NotificationStatus status,
                                        @Param("type") NotificationType type,
                                        @Param("maxAttempts") int maxAttempts, Pageable pageable);
}
