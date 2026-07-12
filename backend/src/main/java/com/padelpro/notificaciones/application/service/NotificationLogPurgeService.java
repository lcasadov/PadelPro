package com.padelpro.notificaciones.application.service;

import com.padelpro.notificaciones.infrastructure.persistence.NotificationLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Retention purge use case for {@code notification_log} (tech-debt #199, RGPD storage limitation).
 *
 * <p>{@code notification_log} is an audit trail that stores {@code recipient} (email) and
 * {@code message} (reservation/payment facts). Without a TTL it grows forever and keeps personal data
 * long past any operational need, which conflicts with the RGPD principles of data minimisation and
 * storage limitation. Rows are therefore deleted once they are older than a configurable retention
 * window ({@code app.notification.retention}, ISO-8601 duration, default {@code P180D} = 6 months).
 *
 * <p><b>Delete vs anonymise:</b> the table is an <em>operational</em> delivery log (used only by the
 * retry job and occasional ADMIN inspection), not a legally-mandated financial record, so old rows
 * are simply removed rather than anonymised in place — this is the simpler and cleaner option and
 * fully removes the personal data. Six months comfortably covers retry/backoff cycles and any
 * realistic support look-back window.
 *
 * <p>Plain object (no Spring stereotype) wired in {@code NotificacionesConfig}, mirroring the other
 * notificaciones services and {@code IdempotencyKeyPurgeService}. A {@link Clock} is injected so the
 * cutoff is deterministic under test.
 */
public class NotificationLogPurgeService {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogPurgeService.class);

    private final NotificationLogJpaRepository repository;
    private final Duration retention;
    private final Clock clock;

    /**
     * @param repository notification-log store
     * @param retention  retention window; rows older than {@code now - retention} are purged (must be
     *                   positive, else construction fails to avoid deleting live rows)
     * @param clock      time source (injected for deterministic tests)
     */
    public NotificationLogPurgeService(NotificationLogJpaRepository repository, Duration retention,
                                       Clock clock) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException(
                    "app.notification.retention must be a positive duration, was: " + retention);
        }
        this.repository = repository;
        this.retention = retention;
        this.clock = clock;
    }

    /**
     * Deletes notification_log rows older than the retention window. Rows created within
     * {@code [now - retention, now]} are preserved so recent deliveries remain auditable and the retry
     * job can still pick up recent {@code FAILED} entries.
     *
     * @return number of rows purged
     */
    @Transactional
    public int purgeExpired() {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minus(retention);
        int purged = repository.deleteCreatedBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} notification_log row(s) older than {} (cutoff {})",
                    purged, retention, threshold);
        }
        return purged;
    }
}
