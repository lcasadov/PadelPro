-- Migration: unique registered-participant per reservation (capability partidas, RN-AUTH-03 backstop)
-- Source of truth: docs/data-model.md §3.4 (participants).
--
-- Defense in depth. The join use case (UnirseReservaService) already rejects a duplicate join in
-- memory while holding the reservation row FOR UPDATE, so a registered user can never occupy two
-- seats of the same reservation. This partial unique index adds a DB-level backstop so the invariant
-- also holds against any future write path or manual data fix. External guests (user_id IS NULL) are
-- exempt: several guests may share the null user_id, so the index is partial on user_id IS NOT NULL.
--
-- Existing data is expected to contain no duplicates (the in-memory guard has always been in place);
-- if any did exist the CREATE would fail loudly rather than silently drop rows.

CREATE UNIQUE INDEX uq_part_user_reservation
    ON participants (reservation_id, user_id)
    WHERE user_id IS NOT NULL;
