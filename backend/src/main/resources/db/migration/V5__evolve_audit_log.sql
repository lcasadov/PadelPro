-- V5: Evolve audit_log table to support richer context fields
-- Introduced by: auditoria change (#149)

ALTER TABLE audit_log
    ALTER COLUMN action TYPE VARCHAR(100),
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS entity_id   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS channel     VARCHAR(20) DEFAULT 'WEB';

CREATE INDEX IF NOT EXISTS idx_audit_log_entity_type
    ON audit_log (entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_channel
    ON audit_log (channel);
