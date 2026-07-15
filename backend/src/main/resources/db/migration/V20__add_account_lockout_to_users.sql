-- =============================================================================
-- V20__add_account_lockout_to_users.sql
-- capability: auth-local · change: bugfix/239 rate-limit-xff-account-lockout (H-1)
--
-- Defensa en profundidad contra fuerza bruta / credential stuffing (OWASP A07:2021).
-- El rate-limiter por IP no basta cuando la IP es spoofeable; el bloqueo de cuenta actúa
-- por credencial y sobrevive a la rotación de IP:
--   - failed_login_attempts: contador de fallos consecutivos; se resetea en login correcto.
--   - locked_until: instante hasta el que la cuenta queda bloqueada (NULL = no bloqueada).
--     Al alcanzar el umbral se fija locked_until = now + ventana y el contador vuelve a 0,
--     de modo que al expirar el bloqueo la cuenta recupera intentos frescos.
--
-- Additive, backwards-compatible: las filas existentes arrancan con 0 fallos y sin bloqueo.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMPTZ;
