-- Migration: payments + idempotency_keys (capability reservas, US-007 / #14)
-- Source of truth: docs/data-model.md §3.5 (payments). Idempotency table per design D2.
--
-- D3: every reservation is created atomically with exactly one payments row (1:1, status=PENDING),
--     amount frozen = price_per_hour × duration_minutes / 60.
-- D2: idempotency_keys gives POST /api/reservas at-most-once semantics per (user_id, key).

-- ─── Enums (data-model §3.5) ─────────────────────────────────────────────────
CREATE TYPE payment_method  AS ENUM ('REDSYS', 'CASH');
CREATE TYPE payment_status  AS ENUM ('PENDING', 'IN_PROGRESS', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED');
CREATE TYPE payment_gateway AS ENUM ('REDSYS', 'STRIPE', 'PAYPAL');

-- ─── Table: payments (data-model §3.5) ───────────────────────────────────────
CREATE TABLE payments (
    id                UUID             NOT NULL DEFAULT gen_random_uuid(),
    reservation_id    UUID             NOT NULL,
    amount            NUMERIC(12,2)    NOT NULL,
    method            payment_method,
    status            payment_status   NOT NULL DEFAULT 'PENDING',
    redsys_order_id   VARCHAR(100),
    payment_url       VARCHAR(500),
    gateway           payment_gateway,
    transaction_id    VARCHAR(100),
    registered_by_id  BIGINT,
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT uq_pay_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_pay_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pay_registered_by FOREIGN KEY (registered_by_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_pay_amount CHECK (amount > 0),
    -- If the method is CASH, registered_by_id must be present (admin who registered the cash payment).
    CONSTRAINT chk_pay_cash_admin CHECK (
        method IS NULL OR method <> 'CASH' OR registered_by_id IS NOT NULL
    )
);

-- Indexes (data-model §3.5). The UNIQUE constraint above already provides the unique B-tree on
-- reservation_id (1:1), so no separate idx_pay_reservation_id is needed.
CREATE INDEX idx_pay_registered_by_id
    ON payments (registered_by_id)
    WHERE registered_by_id IS NOT NULL;
CREATE INDEX idx_pay_status_paid_at
    ON payments (status, paid_at)
    WHERE status = 'PAID';
CREATE UNIQUE INDEX idx_pay_redsys_order_id
    ON payments (redsys_order_id)
    WHERE redsys_order_id IS NOT NULL;
CREATE INDEX idx_pay_pending
    ON payments (created_at)
    WHERE status = 'PENDING';

-- ─── Table: idempotency_keys (design D2) ─────────────────────────────────────
-- At-most-once create semantics: a second POST /api/reservas with the same (user_id, key)
-- returns the already-created reservation instead of inserting a duplicate.
CREATE TABLE idempotency_keys (
    id              BIGSERIAL     NOT NULL,
    idem_key        VARCHAR(255)  NOT NULL,
    user_id         BIGINT        NOT NULL,
    reservation_id  UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uq_idem_user_key UNIQUE (user_id, idem_key),
    CONSTRAINT fk_idem_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_idem_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
);

-- TTL/cleanup support: a deferred job purges keys older than the retention window (design risk note).
CREATE INDEX idx_idem_created_at ON idempotency_keys (created_at);
