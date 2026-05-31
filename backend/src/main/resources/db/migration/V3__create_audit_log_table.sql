-- =============================================================================
-- V3__create_audit_log_table.sql
-- Tabla audit_log — bootstrap-mvp · capability: auth-local · requisito R-5
--
-- Decisiones de diseño:
--   - user_id NULLABLE: un intento de login fallido con email inexistente genera
--     user_id=NULL (R-5.3). Nunca almacenar el email intentado para no acumular
--     datos de personas no registradas (RN-RGPD-03).
--   - ON DELETE SET NULL: si el usuario es anonimizado (derecho al olvido,
--     Art. 17 RGPD), las entradas de auditoría se mantienen pero sin FK.
--     Obligación legal RN-RGPD-02: logs mínimo 2 años. Las filas nunca se borran.
--   - details NULLABLE: campo de contexto libre. NUNCA debe almacenar contraseña,
--     token JWT, código OTP ni dato de tarjeta (RN-RGPD-04).
--   - Acciones válidas en bootstrap-mvp: USER_REGISTERED, LOGIN_SUCCESS,
--     LOGIN_FAILURE, TOKEN_TAMPERED.
--   - action VARCHAR(50): suficiente para los códigos de acción de todo el
--     sistema (ver data-model.md §3.7 para la lista completa).
-- =============================================================================

CREATE TABLE audit_log (
    id          BIGSERIAL       PRIMARY KEY,
    action      VARCHAR(50)     NOT NULL,
    user_id     BIGINT,
    ip_address  VARCHAR(45),
    details     TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_log_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Índice para consultas de auditoría por usuario (panel admin)
CREATE INDEX idx_audit_log_user_id
    ON audit_log (user_id);

-- Índice para consultas por rango temporal
CREATE INDEX idx_audit_log_created_at
    ON audit_log (created_at);

-- Índice para filtrar y contar por tipo de acción (informes de seguridad)
CREATE INDEX idx_audit_log_action
    ON audit_log (action);

-- Índice compuesto para el caso de uso más frecuente del admin:
-- historial de acciones de un usuario ordenado por tiempo
CREATE INDEX idx_audit_log_user_time
    ON audit_log (user_id, created_at DESC);
