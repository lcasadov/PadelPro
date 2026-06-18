package com.padelpro.reservas.domain.port.out;

import com.padelpro.reservas.domain.model.IdempotencyKey;
import com.padelpro.reservas.domain.model.Reservation;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound write port for reservations (capability reservas, US-007).
 *
 * <p>The {@code save} of a reservation cascades its participants. The gist-exclusion violation
 * (design D1) is translated to {@code SlotConflictException} by the adapter implementing this port.
 */
public interface ReservationCommandPort {

    /**
     * Persist a new (or updated) reservation including its participants.
     *
     * @throws com.padelpro.reservas.domain.exception.SlotConflictException on overlap (gist constraint)
     */
    Reservation save(Reservation reservation);

    /** Look up a reservation by id (without forcing participant/payment fetch). */
    Optional<Reservation> findById(UUID id);

    /** Persist an idempotency record linking (user_id, key) → reservation. */
    IdempotencyKey saveIdempotencyKey(IdempotencyKey key);

    /** Resolve a previously stored idempotency record for (user_id, key). */
    Optional<IdempotencyKey> findIdempotencyKey(Long userId, String key);
}
