package com.padelpro.reservas.infrastructure.schedule;

import com.padelpro.reservas.application.service.IdempotencyKeyPurgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdempotencyKeyPurgeJob}: the scheduled entry point delegates to the service's
 * purge logic and never lets an exception escape (which would kill the scheduler thread).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyKeyPurgeJob — scheduled delegation, exception-safe")
class IdempotencyKeyPurgeJobTest {

    @Mock
    private IdempotencyKeyPurgeService purgeService;

    @Test
    @DisplayName("delegates the cycle to IdempotencyKeyPurgeService.purgeExpired()")
    void delegates_to_service() {
        when(purgeService.purgeExpired()).thenReturn(3);
        IdempotencyKeyPurgeJob job = new IdempotencyKeyPurgeJob(purgeService);

        job.purgeExpiredKeys();

        verify(purgeService).purgeExpired();
    }

    @Test
    @DisplayName("a failure inside the cycle never propagates out of the scheduled method")
    void swallows_exceptions() {
        when(purgeService.purgeExpired()).thenThrow(new RuntimeException("db down"));
        IdempotencyKeyPurgeJob job = new IdempotencyKeyPurgeJob(purgeService);

        assertThatCode(job::purgeExpiredKeys).doesNotThrowAnyException();
    }
}
