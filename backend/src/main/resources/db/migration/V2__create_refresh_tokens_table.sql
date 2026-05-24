-- =============================================================================
-- V2__create_refresh_tokens_table.sql
-- Tabla refresh_tokens — bootstrap-mvp · capability: auth-local
--
-- Decisiones de diseño:
--   - token_hash VARCHAR(64): SHA-256 del refresh token UUID v4 → siempre 64
--     chars hex. Nunca se almacena el token en claro (RN-RGPD-04).
--   - ip_address VARCHAR(45): cubre tanto IPv4 (15 chars) como IPv6 (39 chars),
--     incluyendo la notación IPv4-mapped IPv6 (45 chars máximo).
--   - ON DELETE CASCADE: si el usuario se elimina o anonimiza, sus tokens de
--     refresco se eliminan automáticamente. Esto es correcto para infraestructura
--     de seguridad (los tokens son propiedad del usuario).
--   - Índice parcial idx_rt_user_active filtra WHERE revoked = false para
--     acelerar la consulta de tokens activos por usuario sin escanear tokens
--     ya revocados.
-- =============================================================================

CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_token_hash_unique
        UNIQUE (token_hash)
);

-- Índice para búsqueda por user_id (revocar todos los tokens de un usuario)
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

-- Índice para búsqueda por token_hash (validación de refresh — O(log n))
CREATE INDEX idx_refresh_tokens_token_hash
    ON refresh_tokens (token_hash);

-- Índice parcial para listar tokens activos por usuario (evita escanear revocados)
CREATE INDEX idx_rt_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked = false;

-- Índice para limpieza nocturna de tokens expirados
CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
