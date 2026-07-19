-- Migration: bind reservation OTPs to their reservation (capability bot-telegram-reservas, D-4).
-- Source of truth: openspec/changes/bot-telegram-reservas/design.md (D-4, option a).
--
-- Problem: RESERVATION_CONFIRM / CANCELLATION_CONFIRM codes were keyed only by (user_id, type), so
-- with a confirm pending on reservation X and a cancellation pending on Y, `/confirmar X <otp>` could
-- not tell which operation the code belonged to and picked one by type precedence — risking cancelling
-- X when the user meant to confirm it. Binding each reservation OTP to its reservation removes the
-- ambiguity: the code is resolved by (user_id, reservation_id) and its type then unambiguously says
-- confirm-vs-cancel.
--
-- Additive + nullable: TELEGRAM_LINK / PASSWORD_RESET codes are not reservation-scoped and keep
-- reservation_id NULL, so existing flows are unchanged and the change is reversible (drop the column).

ALTER TABLE otp_codes ADD COLUMN IF NOT EXISTS reservation_id UUID;

-- Hot path for the Telegram dispatcher: resolve the active OTP bound to a given reservation for a user
-- (D-4). Partial index keeps non-reservation codes (the majority) out of the index.
CREATE INDEX IF NOT EXISTS idx_otp_user_reservation
    ON otp_codes (user_id, reservation_id)
    WHERE reservation_id IS NOT NULL;
