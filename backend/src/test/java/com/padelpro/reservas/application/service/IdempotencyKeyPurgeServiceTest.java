package com.padelpro.reservas.application.service;

import com.padelpro.reservas.infrastructure.persistence.IdempotencyKeyJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdempotencyKeyPurgeService} (Idempotency-Key TTL purge).
 *
 * <p>Uses a fixed {@link Clock} so the computed cutoff ({@code now - ttl}) is deterministic — the core
 * guarantee being that keys within the TTL window are preserved and only older rows are purged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyPurgeService — TTL cutoff + delegation")
class IdempotencyKeyPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private IdempotencyKeyJpaRepository repository;

    @Test
    @DisplayName("purges rows created before now - ttl and returns the deleted count")
    void purges_with_correct_cutoff_and_returns_count() {
        Duration ttl = Duration.ofHours(24);
        when(repository.deleteExpiredBefore(any())).thenReturn(7);
        IdempotencyKeyPurgeService service = new IdempotencyKeyPurgeService(repository, ttl, fixedClock);

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(7);
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusHours(24));
    }

    @Test
    @DisplayName("returns 0 (no-op) when nothing is expired")
    void returns_zero_when_nothing_expired() {
        when(repository.deleteExpiredBefore(any())).thenReturn(0);
        IdempotencyKeyPurgeService service =
                new IdempotencyKeyPurgeService(repository, Duration.ofHours(24), fixedClock);

        assertThat(service.purgeExpired()).isZero();
    }

    @Test
    @DisplayName("a shorter TTL moves the cutoff closer to now")
    void shorter_ttl_moves_cutoff() {
        when(repository.deleteExpiredBefore(any())).thenReturn(1);
        IdempotencyKeyPurgeService service =
                new IdempotencyKeyPurgeService(repository, Duration.ofHours(1), fixedClock);

        service.purgeExpired();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusHours(1));
    }

    @Test
    @DisplayName("rejects a non-positive TTL at construction (would purge live keys)")
    void rejects_non_positive_ttl() {
        assertThatThrownBy(() ->
                new IdempotencyKeyPurgeService(repository, Duration.ZERO, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new IdempotencyKeyPurgeService(repository, Duration.ofHours(-1), fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new IdempotencyKeyPurgeService(repository, null, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
