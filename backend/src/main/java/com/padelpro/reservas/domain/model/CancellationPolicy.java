package com.padelpro.reservas.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Cancellation deadline policy (RN-RES-04, D6).
 *
 * <p>A user-initiated cancellation is allowed only when at least {@code cancellation_deadline_hours}
 * remain until {@code reservation_date + start_time}. If the deadline has passed, the user cannot
 * cancel (422); an ADMIN bypasses this policy entirely (D-RES-03, handled in the admin path).
 *
 * <p>Refund eligibility mirrors the same window: a cancellation within the deadline refunds a PAID
 * payment; outside it would not — but since the user path rejects late cancellations outright, the
 * refund branch only fires on allowed cancellations.
 */
public final class CancellationPolicy {

    private CancellationPolicy() {
    }

    /**
     * Whether a user may cancel now, given the reservation start and the configured deadline.
     *
     * @param reservationDate the reservation date
     * @param startTime       the reservation start time
     * @param deadlineHours   {@code system_config.cancellation_deadline_hours}
     * @param now             the current instant (local)
     * @return true if at least {@code deadlineHours} remain until the start
     */
    public static boolean canUserCancel(LocalDate reservationDate, LocalTime startTime,
                                        int deadlineHours, LocalDateTime now) {
        LocalDateTime start = LocalDateTime.of(reservationDate, startTime);
        LocalDateTime deadline = start.minusHours(deadlineHours);
        return !now.isAfter(deadline);
    }

    /** Hours remaining until the reservation start (negative if already started). */
    public static long hoursUntilStart(LocalDate reservationDate, LocalTime startTime,
                                       LocalDateTime now) {
        LocalDateTime start = LocalDateTime.of(reservationDate, startTime);
        return Duration.between(now, start).toHours();
    }
}
