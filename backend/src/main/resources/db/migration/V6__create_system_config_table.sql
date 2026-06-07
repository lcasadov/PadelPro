-- Migration: Create singleton system_config table (configuracion-club)
-- D-CONF-03: Singleton enforced via CHECK constraint (id = 1)
-- D-CONF-02: Secrets encrypted with AES-256-GCM (encrypted in storage)

CREATE TABLE IF NOT EXISTS system_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    club_name VARCHAR(255) NOT NULL,
    club_description TEXT,
    pista_state VARCHAR(50) NOT NULL DEFAULT 'ACTIVA' CHECK (pista_state IN ('ACTIVA', 'MANTENIMIENTO')),
    payment_gateway VARCHAR(50) NOT NULL DEFAULT 'CASH' CHECK (payment_gateway IN ('CASH', 'REDSYS')),
    telegram_bot_token TEXT,
    redsys_merchant_id TEXT,
    redsys_merchant_key TEXT,
    max_participants_per_pista INTEGER NOT NULL DEFAULT 4 CHECK (max_participants_per_pista > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id BIGINT,
    CONSTRAINT fk_updated_by_user FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Insert default singleton row
INSERT INTO system_config (
    id,
    club_name,
    club_description,
    pista_state,
    payment_gateway,
    max_participants_per_pista
) VALUES (
    1,
    'Mi Club de Pádel',
    'Club profesional de pádel',
    'ACTIVA',
    'CASH',
    4
) ON CONFLICT DO NOTHING;

-- Create index on id (already a PK, but explicit for clarity)
CREATE INDEX IF NOT EXISTS idx_system_config_id ON system_config(id);
