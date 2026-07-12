package com.padelpro.notificaciones.application.service;

import com.padelpro.notificaciones.infrastructure.persistence.NotificationLogJpaRepository;
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
 * Unit tests for {@link NotificationLogPurgeService} (notification_log retention purge, #199).
 *
 * <p>Uses a fixed {@link Clock} so the computed cutoff ({@code now - retention}) is deterministic —
 * the core guarantee being that rows within the retention window are preserved and only older rows
 * are purged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationLogPurgeService — retention cutoff + delegation")
class NotificationLogPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private NotificationLogJpaRepository repository;

    @Test
    @DisplayName("purges rows created before now - retention and returns the deleted count")
    void purges_with_correct_cutoff_and_returns_count() {
        Duration retention = Duration.ofDays(180);
        when(repository.deleteCreatedBefore(any())).thenReturn(12);
        NotificationLogPurgeService service =
                new NotificationLogPurgeService(repository, retention, fixedClock);

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(12);
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(180));
    }

    @Test
    @DisplayName("returns 0 (no-op) when nothing is expired")
    void returns_zero_when_nothing_expired() {
        when(repository.deleteCreatedBefore(any())).thenReturn(0);
        NotificationLogPurgeService service =
                new NotificationLogPurgeService(repository, Duration.ofDays(180), fixedClock);

        assertThat(service.purgeExpired()).isZero();
    }

    @Test
    @DisplayName("a shorter retention moves the cutoff closer to now (preserves fewer rows)")
    void shorter_retention_moves_cutoff() {
        when(repository.deleteCreatedBefore(any())).thenReturn(1);
        NotificationLogPurgeService service =
                new NotificationLogPurgeService(repository, Duration.ofDays(30), fixedClock);

        service.purgeExpired();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30));
    }

    @Test
    @DisplayName("rejects a non-positive retention at construction (would purge live rows)")
    void rejects_non_positive_retention() {
        assertThatThrownBy(() ->
                new NotificationLogPurgeService(repository, Duration.ZERO, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new NotificationLogPurgeService(repository, Duration.ofDays(-1), fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new NotificationLogPurgeService(repository, null, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
