-- Migration: bloqueo_pista (change bloqueos-pista-eventos, D2)
-- Source of truth: openspec/changes/bloqueos-pista-eventos/design.md (D1–D2).
--
-- One row per blocked 60-min slot of a date. UNIQUE(fecha, hora) makes the block idempotent and
-- prevents duplicates. The availability calculation queries by fecha, so an index on fecha is added.
-- Additive migration (no pre-existing data). Next free version after V18 (add-simulado-payment-method).

CREATE TABLE bloqueo_pista (
    id                 BIGSERIAL    NOT NULL,
    fecha              DATE         NOT NULL,
    hora               TIME         NOT NULL,
    motivo             TEXT,
    created_by_user_id BIGINT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT bloqueo_pista_pkey PRIMARY KEY (id),
    CONSTRAINT uq_bloqueo_fecha_hora UNIQUE (fecha, hora),
    CONSTRAINT fk_bloqueo_created_by FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL
);

-- Availability reads all blocked hours of a date; back the (fecha) lookup with a B-tree index.
CREATE INDEX idx_bloqueo_fecha ON bloqueo_pista (fecha);
