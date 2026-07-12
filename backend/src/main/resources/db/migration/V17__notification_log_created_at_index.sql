-- Migration: index supporting the notification_log retention purge (capability notificaciones,
-- tech-debt #199 — RGPD retention/purge of notification_log).
--
-- notification_log stores recipient (email) and message (reservation/payment data) and previously
-- grew without bound (no TTL). A scheduled retention job now deletes rows older than
-- app.notification.retention (default P180D) via "DELETE ... WHERE created_at < :threshold". This
-- index backs that range scan so the periodic purge does not sequentially scan the whole table.
-- Mirrors idx_idem_created_at (V9), which backs the analogous idempotency_keys purge.

CREATE INDEX IF NOT EXISTS idx_notif_created_at ON notification_log (created_at);
