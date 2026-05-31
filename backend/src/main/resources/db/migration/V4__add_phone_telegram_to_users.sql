-- =============================================================================
-- V4__add_phone_telegram_to_users.sql
-- Añade campos phone y telegram a la tabla users — change: usuarios
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS telegram_chat_id   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS telegram_linked_at TIMESTAMPTZ;
