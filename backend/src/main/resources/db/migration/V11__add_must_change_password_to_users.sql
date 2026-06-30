-- =============================================================================
-- V11__add_must_change_password_to_users.sql
-- capability: auth-local · change: acceso-cuenta-prod (D9)
--
-- Adds the must_change_password flag. When TRUE (set by an admin password reset),
-- the next login forces the user to set a new password before normal use; the flag
-- is cleared once the password is changed.
--
-- Additive, backwards-compatible: existing rows default to FALSE.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
