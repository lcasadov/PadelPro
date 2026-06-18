package com.padelpro.reservas.domain.model;

import com.padelpro.reservas.domain.exception.InvalidReservaStateException;

import java.util.Map;
import java.util.Set;

/**
 * Unidirectional reservation state machine (D6).
 *
 * <pre>
 *   PENDING_CONFIRMATION → CONFIRMED → COMPLETED
 *                       ↘            ↘
 *                         CANCELLED
 * </pre>
 *
 * <p>{@code COMPLETED} and {@code CANCELLED} are terminal. Any transition not listed here is
 * rejected with HTTP 422 via {@link InvalidReservaStateException}.
 */
public final class ReservationStateMachine {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = Map.of(
            ReservationStatus.PENDING_CONFIRMATION,
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
            ReservationStatus.CONFIRMED,
            Set.of(ReservationStatus.COMPLETED, ReservationStatus.CANCELLED),
            ReservationStatus.COMPLETED, Set.of(),
            ReservationStatus.CANCELLED, Set.of()
    );

    private ReservationStateMachine() {
    }

    /** Whether {@code from → to} is a permitted transition. */
    public static boolean canTransition(ReservationStatus from, ReservationStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Validate a transition, throwing 422 if not permitted (D6).
     *
     * @throws InvalidReservaStateException if {@code from → to} is invalid
     */
    public static void assertCanTransition(ReservationStatus from, ReservationStatus to) {
        if (from == to) {
            throw new InvalidReservaStateException(
                    "INVALID_STATE_TRANSITION",
                    "La reserva ya se encuentra en estado " + to);
        }
        if (!canTransition(from, to)) {
            throw new InvalidReservaStateException(
                    "INVALID_STATE_TRANSITION",
                    "Transición de estado no permitida: " + from + " → " + to);
        }
    }
}
