-- =============================================================================
-- V1__create_users_table.sql
-- Tabla users — bootstrap-mvp · capability: auth-local
--
-- Decisiones de diseño:
--   - VARCHAR + CHECK constraints en lugar de tipos ENUM nativos de PostgreSQL.
--     Flyway + Spring Boot gestionan mejor los VARCHAR en migraciones futuras
--     (ALTER TYPE ADD VALUE en ENUMs requiere transacción separada en PG < 12).
--   - login es el campo usado para autenticación (alias de email en v1.0).
--     Lowercase enforced en la capa de servicio, no en DDL.
--   - password_hash almacenará BCrypt cost 12. Nunca registrar en logs (RN-RGPD-04).
--   - Soft-delete mediante status=INACTIVE. Nunca DELETE FROM users.
-- =============================================================================

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    login           VARCHAR(100)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    role            VARCHAR(10)     NOT NULL DEFAULT 'USER',
    status          VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    registered_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ,
    CONSTRAINT users_login_unique   UNIQUE (login),
    CONSTRAINT users_email_unique   UNIQUE (email),
    CONSTRAINT users_role_check     CHECK (role   IN ('ADMIN', 'USER')),
    CONSTRAINT users_status_check   CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE'))
);

-- Índice sobre email para búsquedas de login (Q8 — autenticación O(log n))
CREATE INDEX idx_users_email ON users (email);

-- Índice compuesto (status, role) para listados de admin filtrados
CREATE INDEX idx_users_status_role ON users (status, role);

-- =============================================================================
-- Trigger para mantener updated_at sincronizado en cada UPDATE
-- =============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
