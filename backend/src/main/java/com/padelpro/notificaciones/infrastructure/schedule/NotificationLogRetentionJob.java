package com.padelpro.notificaciones.infrastructure.schedule;

import com.padelpro.notificaciones.application.service.NotificationLogPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled retention purge of {@code notification_log} (tech-debt #199, RGPD storage limitation).
 * Delegates to {@link NotificationLogPurgeService#purgeExpired()}, which removes rows older than
 * {@code app.notification.retention} (default {@code P180D}). Scheduling is enabled globally by
 * {@code notificaciones.SchedulingConfig}.
 *
 * <p>Disabled in tests ({@code app.notification.purge.enabled=false}) so scheduling never interferes
 * with deterministic assertions; the purge logic itself is tested directly on the service.
 */
@Component
@ConditionalOnProperty(name = "app.notification.purge.enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationLogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogRetentionJob.class);

    private final NotificationLogPurgeService purgeService;

    public NotificationLogRetentionJob(NotificationLogPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    /**
     * Purge expired notification_log rows. Runs on a fixed delay after the previous run completes so a
     * slow DELETE never causes overlapping cycles. Interval configurable via
     * {@code app.notification.purge.delay-ms} (default 24h).
     */
    @Scheduled(fixedDelayString = "${app.notification.purge.delay-ms:86400000}",
            initialDelayString = "${app.notification.purge.initial-delay-ms:86400000}")
    public void purgeExpiredNotifications() {
        try {
            purgeService.purgeExpired();
        } catch (Exception ex) {
            // A scheduled job must never let an exception escape and kill the scheduler thread.
            log.warn("Notification-log retention purge cycle failed: {}", ex.getMessage(), ex);
        }
    }
}
