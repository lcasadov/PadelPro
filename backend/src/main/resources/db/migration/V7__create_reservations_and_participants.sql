-- Migration: Create reservations + participants tables (capability disponibilidad-pistas, US-006 / #13)
-- Source of truth: docs/data-model.md §3.3 (reservations) and §3.4 (participants).
--
-- D-RES-01 (FINAL, Option A): anti-overlap guaranteed at DB level via an EXCLUDE USING gist
-- constraint over the tsrange(date+start, date+end, '[)'), partial WHERE status <> 'CANCELLED'.
-- Requires the btree_gist extension to combine the equality operator on reservation_date with
-- the range overlap operator inside a single GiST exclusion constraint.

-- Extension required for the exclusion constraint (equality + range in one GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ─── Enums ──────────────────────────────────────────────────────────────────
CREATE TYPE reservation_status  AS ENUM ('PENDING_CONFIRMATION', 'CONFIRMED', 'CANCELLED', 'COMPLETED');
CREATE TYPE reservation_channel AS ENUM ('WEB', 'TELEGRAM');

-- ─── Table: reservations (data-model §3.3) ───────────────────────────────────
CREATE TABLE reservations (
    id                   UUID                 NOT NULL DEFAULT gen_random_uuid(),
    owner_id             BIGINT               NOT NULL,
    reservation_date     DATE                 NOT NULL,
    start_time           TIME                 NOT NULL,
    end_time             TIME                 NOT NULL,
    duration_minutes     INTEGER              NOT NULL,
    status               reservation_status   NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    channel              reservation_channel  NOT NULL,
    notes                TEXT,
    telegram_message_id  VARCHAR(50),
    cancellation_reason  VARCHAR(255),
    created_at           TIMESTAMPTZ          NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ          NOT NULL DEFAULT now(),
    CONSTRAINT reservations_pkey PRIMARY KEY (id),
    CONSTRAINT fk_res_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_res_duration CHECK (duration_minutes IN (60, 90, 120, 150, 180)),
    CONSTRAINT chk_res_end_time CHECK (end_time = (start_time + (duration_minutes || ' minutes')::INTERVAL)),
    CONSTRAINT chk_res_start_minutes CHECK (EXTRACT(MINUTE FROM start_time) IN (0, 30))
);

-- Indexes (data-model §3.3)
CREATE INDEX idx_res_owner_id   ON reservations (owner_id);
CREATE INDEX idx_res_date_status
    ON reservations (reservation_date, status)
    WHERE status <> 'CANCELLED';
CREATE INDEX idx_res_date_start ON reservations (reservation_date, start_time);
CREATE INDEX idx_res_owner_date ON reservations (owner_id, reservation_date DESC);
CREATE INDEX idx_res_owner_status
    ON reservations (owner_id, status)
    WHERE status IN ('PENDING_CONFIRMATION', 'CONFIRMED');

-- DB-level anti-overlap barrier (D-RES-01). Two active reservations on the same date
-- whose [start, end) ranges overlap are rejected. CANCELLED reservations are exempt.
ALTER TABLE reservations ADD CONSTRAINT excl_res_no_overlap
    EXCLUDE USING gist (
        reservation_date WITH =,
        tsrange(
            reservation_date + start_time,
            reservation_date + end_time,
            '[)'
        ) WITH &&
    )
    WHERE (status <> 'CANCELLED');

-- ─── Table: participants (data-model §3.4) ───────────────────────────────────
CREATE TABLE participants (
    id              BIGSERIAL            NOT NULL,
    reservation_id  UUID                 NOT NULL,
    user_id         BIGINT,
    external_name   VARCHAR(100),
    external_phone  VARCHAR(20),
    slot_position   INTEGER              NOT NULL,
    is_owner        BOOLEAN              NOT NULL DEFAULT false,
    joined_via      reservation_channel  NOT NULL,
    joined_at       TIMESTAMPTZ          NOT NULL DEFAULT now(),
    CONSTRAINT participants_pkey PRIMARY KEY (id),
    CONSTRAINT fk_part_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_part_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_part_slot CHECK (slot_position BETWEEN 1 AND 4),
    CONSTRAINT chk_part_user_or_external CHECK (
        (user_id IS NOT NULL AND external_name IS NULL) OR
        (user_id IS NULL     AND external_name IS NOT NULL)
    ),
    CONSTRAINT chk_part_phone_format CHECK (
        external_phone IS NULL OR external_phone ~ '^\+[1-9]\d{7,14}$'
    )
);

-- Indexes (data-model §3.4)
CREATE INDEX idx_part_reservation_id ON participants (reservation_id);
CREATE INDEX idx_part_user_id
    ON participants (user_id)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX idx_part_slot_unique
    ON participants (reservation_id, slot_position);
CREATE INDEX idx_part_user_reservation
    ON participants (user_id, reservation_id)
    WHERE user_id IS NOT NULL;
-- Only one owner per reservation
CREATE UNIQUE INDEX idx_part_one_owner
    ON participants (reservation_id)
    WHERE is_owner = true;
