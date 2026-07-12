package com.padelpro.reservas.application.service;

import com.padelpro.reservas.infrastructure.persistence.IdempotencyKeyJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * TTL purge use case for {@code idempotency_keys} (design D2, V9 "deferred job" note).
 *
 * <p>The {@code idempotency_keys} table only carries {@code created_at} (no {@code expires_at}), and
 * an {@code Idempotency-Key} only needs to protect short-window client retries of
 * {@code POST /api/reservas}. Rows are therefore purged once they are older than a configurable TTL
 * ({@code app.idempotency.ttl}, default 24h) instead of being kept forever, which would let the table
 * grow without bound.
 *
 * <p>Plain object (no Spring stereotype) wired in {@code ReservasConfig}, mirroring the other reservas
 * services. A {@link Clock} is injected so the cutoff is deterministic under test.
 */
public class IdempotencyKeyPurgeService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurgeService.class);

    private final IdempotencyKeyJpaRepository repository;
    private final Duration ttl;
    private final Clock clock;

    /**
     * @param repository idempotency-key store
     * @param ttl        retention window; rows older than {@code now - ttl} are purged (must be positive)
     * @param clock      time source (injected for deterministic tests)
     */
    public IdempotencyKeyPurgeService(IdempotencyKeyJpaRepository repository, Duration ttl, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.idempotency.ttl must be a positive duration, was: " + ttl);
        }
        this.repository = repository;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Deletes idempotency keys older than the TTL window. Rows created within {@code [now - ttl, now]}
     * are preserved so an in-flight retry still short-circuits.
     *
     * @return number of rows purged
     */
    @Transactional
    public int purgeExpired() {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minus(ttl);
        int purged = repository.deleteExpiredBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} idempotency key(s) older than {} (cutoff {})", purged, ttl, threshold);
        }
        return purged;
    }
}
