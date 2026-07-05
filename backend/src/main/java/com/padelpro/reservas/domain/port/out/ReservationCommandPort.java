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

    /**
     * Look up a reservation acquiring a pessimistic write lock on its row ({@code SELECT ... FOR
     * UPDATE}) so a concurrent join serialises on the same reservation (D2). Participants are loaded
     * lazily within the caller's transaction after the lock is held, so the seat count re-read
     * reflects the latest committed state and two joins to the last seat cannot both succeed.
     */
    Optional<Reservation> findByIdForUpdate(UUID id);

    /** Persist an idempotency record linking (user_id, key) → reservation. */
    IdempotencyKey saveIdempotencyKey(IdempotencyKey key);

    /** Resolve a previously stored idempotency record for (user_id, key). */
    Optional<IdempotencyKey> findIdempotencyKey(Long userId, String key);
}
