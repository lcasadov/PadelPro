-- Migration: otp_codes table + Telegram link fields (capability auth-otp-telegram, change
-- auth-otp-telegram-impl). Source of truth: openspec/specs/auth-otp-telegram/spec.md
-- (RN-AUTH-07, RN-TEL-01..03) + design.md (D-OTP-01..05).
--
-- RN-AUTH-07: OTP is 6 digits, TTL 10 min, max 3 attempts, stored as SHA-256, single-use.
-- The code is NEVER stored in clear — only its SHA-256 hex digest lives in code_hash (D-OTP-02).
--
-- The users.telegram_chat_id / telegram_linked_at columns were already added (nullable) by
-- V4__add_phone_telegram_to_users.sql; this migration only adds the uniqueness guarantee
-- (a Telegram chat may be linked to at most one account — spec Requirement 1, scenario 3) and
-- the telegram_webhook_secret secret column on system_config (D-OTP-01).

-- --------------------------------------------------------------------------
-- otp_codes
-- --------------------------------------------------------------------------
CREATE TABLE otp_codes (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    code_hash   VARCHAR(64)   NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    attempts    INTEGER       NOT NULL DEFAULT 0,
    used        BOOLEAN       NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_otp_type CHECK (type IN (
        'TELEGRAM_LINK', 'RESERVATION_CONFIRM', 'CANCELLATION_CONFIRM', 'PASSWORD_RESET')),
    CONSTRAINT chk_otp_attempts CHECK (attempts >= 0)
);

-- Index for the hot path: resolve the caller's active OTP of a given type (verification + invalidation).
CREATE INDEX idx_otp_user_type ON otp_codes (user_id, type);
-- Index for the webhook /vincular path: look up an active OTP by its hash + type (D-OTP-04).
CREATE INDEX idx_otp_hash_type ON otp_codes (code_hash, type);

-- --------------------------------------------------------------------------
-- users.telegram_chat_id uniqueness (RN-TEL: one Telegram chat ↔ one account)
-- Partial unique index so multiple NULLs (unlinked accounts) remain allowed.
-- --------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_telegram_chat_id
    ON users (telegram_chat_id)
    WHERE telegram_chat_id IS NOT NULL;

-- --------------------------------------------------------------------------
-- system_config.telegram_webhook_secret (D-OTP-01: AES-256-GCM encrypted at rest,
-- alongside telegram_bot_token). Nullable — absent until the operator configures the bot.
-- --------------------------------------------------------------------------
ALTER TABLE system_config ADD COLUMN IF NOT EXISTS telegram_webhook_secret TEXT;
