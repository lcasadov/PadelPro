-- Migration: Add pricing + cancellation policy fields to system_config (capability reservas, US-007 / #14)
-- D4 (FINAL, additive): the V6 system_config diverged from docs/data-model.md §3.9 and omitted two
-- fields that the reservas capability needs. They are added additively; the singleton row (id=1)
-- created by V6 takes the defaults without any manual backfill.
--
-- Source of truth: docs/data-model.md §3.9 (price_per_hour, cancellation_deadline_hours).

ALTER TABLE system_config
    ADD COLUMN price_per_hour NUMERIC(12,2) NOT NULL DEFAULT 15.00
        CONSTRAINT chk_cfg_price CHECK (price_per_hour > 0);

ALTER TABLE system_config
    ADD COLUMN cancellation_deadline_hours INTEGER NOT NULL DEFAULT 2
        CONSTRAINT chk_cfg_deadline CHECK (cancellation_deadline_hours >= 0);
