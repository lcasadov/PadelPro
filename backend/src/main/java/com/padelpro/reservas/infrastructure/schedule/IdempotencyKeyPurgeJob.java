package com.padelpro.reservas.infrastructure.schedule;

import com.padelpro.reservas.application.service.IdempotencyKeyPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled TTL purge of {@code idempotency_keys} (design D2, V9 "deferred job" note). Delegates to
 * {@link IdempotencyKeyPurgeService#purgeExpired()}, which removes rows older than
 * {@code app.idempotency.ttl}. Scheduling is enabled globally by {@code notificaciones.SchedulingConfig}.
 *
 * <p>Disabled in tests ({@code app.idempotency.purge.enabled=false}) so scheduling never interferes
 * with deterministic assertions; the purge logic itself is tested directly on the service.
 */
@Component
@ConditionalOnProperty(name = "app.idempotency.purge.enabled", havingValue = "true",
        matchIfMissing = true)
public class IdempotencyKeyPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurgeJob.class);

    private final IdempotencyKeyPurgeService purgeService;

    public IdempotencyKeyPurgeJob(IdempotencyKeyPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    /**
     * Purge expired idempotency keys. Runs on a fixed delay after the previous run completes so a slow
     * DELETE never causes overlapping cycles. Interval configurable via
     * {@code app.idempotency.purge.delay-ms} (default 1h).
     */
    @Scheduled(fixedDelayString = "${app.idempotency.purge.delay-ms:3600000}",
            initialDelayString = "${app.idempotency.purge.initial-delay-ms:3600000}")
    public void purgeExpiredKeys() {
        try {
            purgeService.purgeExpired();
        } catch (Exception ex) {
            // A scheduled job must never let an exception escape and kill the scheduler thread.
            log.warn("Idempotency-key purge cycle failed: {}", ex.getMessage(), ex);
        }
    }
}
