-- Migration: Telegram notification support (capability notificaciones, change
-- notificaciones-telegram / #212).
-- Source of truth: openspec/changes/notificaciones-telegram/design.md (D2, D3) +
-- openspec/specs/notificaciones/spec.md (Req 6, notification_log types EMAIL/TELEGRAM_DIRECT/
-- TELEGRAM_GROUP).
--
-- Two additive, backward-compatible changes:
--   1. system_config.telegram_group_id — the club's Telegram group chat id used to broadcast a
--      message when a reservation is confirmed (Req 6, D2). NOT a secret (a chat id is not
--      sensitive) so it is stored in plain text, unlike telegram_bot_token / telegram_webhook_secret.
--      Nullable — when absent no group message is published (clean degradation).
--   2. Relax the notification_log type CHECK to allow the two Telegram channels alongside EMAIL, so
--      each Telegram delivery can be audited like an email (D3, RN-NOT-03).

ALTER TABLE system_config ADD COLUMN IF NOT EXISTS telegram_group_id VARCHAR(64);

ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS chk_notif_type;
ALTER TABLE notification_log ADD CONSTRAINT chk_notif_type
    CHECK (type IN ('EMAIL', 'TELEGRAM_DIRECT', 'TELEGRAM_GROUP'));
