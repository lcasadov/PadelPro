-- Migration: add redsys_terminal to system_config (pagos-redsys-online, D4)
--
-- The Redsys terminal number is a merchant-specific datum (distinct from merchant_id / merchant_key)
-- required to build the TPV form. Stored encrypted with AES-256-GCM like the other Redsys secrets
-- (redsys_merchant_id, redsys_merchant_key). Nullable — when absent the application defaults to "1".
ALTER TABLE system_config ADD COLUMN IF NOT EXISTS redsys_terminal TEXT;
