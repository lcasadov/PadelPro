package com.padelpro.reservas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CancellationPolicy} (task 8.1, RN-RES-04).
 *
 * <p>A user may cancel only when at least {@code cancellation_deadline_hours} remain until
 * {@code reservation_date + start_time}. The {@code now} instant is injected so the test is
 * deterministic and independent of the wall clock.
 */
@DisplayName("Unit — CancellationPolicy")
class CancellationPolicyTest {

    private static final LocalDate DATE = LocalDate.of(2030, 6, 1);
    private static final LocalTime START = LocalTime.of(18, 0); // reservation starts 2030-06-01 18:00
    private static final int DEADLINE_HOURS = 2;                // deadline = 2030-06-01 16:00

    // 8.1 — comfortably before the deadline → allowed
    @Test
    @DisplayName("permite cancelar con holgura antes del plazo")
    void should_allow_when_well_before_deadline() {
        LocalDateTime now = LocalDateTime.of(2030, 6, 1, 10, 0); // 8h before start

        assertThat(CancellationPolicy.canUserCancel(DATE, START, DEADLINE_HOURS, now)).isTrue();
    }

    // 8.1 — exactly at the deadline boundary → still allowed (not strictly after)
    @Test
    @DisplayName("permite cancelar justo en el instante límite (16:00)")
    void should_allow_at_exact_deadline_boundary() {
        LocalDateTime now = LocalDateTime.of(2030, 6, 1, 16, 0); // == deadline

        assertThat(CancellationPolicy.canUserCancel(DATE, START, DEADLINE_HOURS, now)).isTrue();
    }

    // 8.1 — one minute past the deadline → rejected
    @Test
    @DisplayName("rechaza cancelar un minuto pasado el plazo")
    void should_reject_one_minute_past_deadline() {
        LocalDateTime now = LocalDateTime.of(2030, 6, 1, 16, 1); // 1 min after deadline

        assertThat(CancellationPolicy.canUserCancel(DATE, START, DEADLINE_HOURS, now)).isFalse();
    }

    // 8.1 — after the reservation already started → rejected
    @Test
    @DisplayName("rechaza cancelar cuando la reserva ya empezó")
    void should_reject_after_start() {
        LocalDateTime now = LocalDateTime.of(2030, 6, 1, 18, 30);

        assertThat(CancellationPolicy.canUserCancel(DATE, START, DEADLINE_HOURS, now)).isFalse();
    }

    // 8.1 — deadline of 0 hours: allowed right up to the start instant
    @Test
    @DisplayName("con plazo 0h permite cancelar hasta el inicio exacto")
    void should_allow_until_start_when_deadline_zero() {
        LocalDateTime atStart = LocalDateTime.of(2030, 6, 1, 18, 0);
        LocalDateTime afterStart = LocalDateTime.of(2030, 6, 1, 18, 1);

        assertThat(CancellationPolicy.canUserCancel(DATE, START, 0, atStart)).isTrue();
        assertThat(CancellationPolicy.canUserCancel(DATE, START, 0, afterStart)).isFalse();
    }

    // 8.1 — hoursUntilStart helper: positive before, negative after
    @Test
    @DisplayName("hoursUntilStart devuelve horas restantes (negativas si ya empezó)")
    void should_report_hours_until_start() {
        LocalDateTime eightBefore = LocalDateTime.of(2030, 6, 1, 10, 0);
        LocalDateTime oneAfter = LocalDateTime.of(2030, 6, 1, 19, 0);

        assertThat(CancellationPolicy.hoursUntilStart(DATE, START, eightBefore)).isEqualTo(8);
        assertThat(CancellationPolicy.hoursUntilStart(DATE, START, oneAfter)).isEqualTo(-1);
    }
}
