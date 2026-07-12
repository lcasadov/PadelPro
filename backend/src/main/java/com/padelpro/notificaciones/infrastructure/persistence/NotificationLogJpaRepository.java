package com.padelpro.notificaciones.infrastructure.persistence;

import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.domain.model.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    /**
     * Bulk-deletes notification_log rows created strictly before {@code threshold}, returning the
     * number of rows removed. The audit log keeps {@code recipient} (email) and {@code message}
     * (reservation/payment data); past a retention window ({@code app.notification.retention}) these
     * personal data are no longer needed and must not be kept indefinitely (RGPD data minimisation /
     * storage limitation, #199). Backed by {@code idx_notif_created_at} (V17).
     *
     * <p>{@code @Modifying} DELETE — requires an active transaction, provided by the calling service.
     * Rows still inside the retention window ({@code created_at >= threshold}) are preserved.
     *
     * @param threshold cutoff instant ({@code now - retention}); rows with {@code created_at <} this
     *                  are purged
     * @return count of purged rows
     */
    @Modifying
    @Query("DELETE FROM NotificationLog n WHERE n.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") OffsetDateTime threshold);
}
