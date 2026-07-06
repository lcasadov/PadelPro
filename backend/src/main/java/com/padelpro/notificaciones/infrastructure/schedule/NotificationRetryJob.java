package com.padelpro.notificaciones.infrastructure.schedule;

import com.padelpro.notificaciones.application.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled retry of failed email notifications (change {@code notificaciones-eventos-email}, Req 3 /
 * D3). Delegates to {@link EmailNotificationService#retryFailed()}, which re-sends {@code FAILED}
 * entries still below the attempt cap honouring an exponential backoff; after 3 failed attempts an
 * entry stays {@code FAILED} and is no longer picked up.
 *
 * <p>Disabled in tests ({@code app.notifications.retry.enabled=false}) so scheduling never interferes
 * with deterministic assertions; the retry logic itself is tested directly on the service.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.retry.enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);

    private final EmailNotificationService emailNotificationService;

    public NotificationRetryJob(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Re-attempt failed notifications. Runs on a fixed delay after the previous run completes so a
     * slow SMTP never causes overlapping cycles. Interval configurable via
     * {@code app.notifications.retry.delay-ms} (default 60s).
     */
    @Scheduled(fixedDelayString = "${app.notifications.retry.delay-ms:60000}",
            initialDelayString = "${app.notifications.retry.initial-delay-ms:60000}")
    public void retryFailedNotifications() {
        try {
            int attempted = emailNotificationService.retryFailed();
            if (attempted > 0) {
                log.info("Notification retry cycle re-attempted {} failed email(s)", attempted);
            }
        } catch (Exception ex) {
            // A scheduled job must never let an exception escape and kill the scheduler thread.
            log.warn("Notification retry cycle failed: {}", ex.getMessage(), ex);
        }
    }
}
