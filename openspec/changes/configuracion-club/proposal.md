# Proposal: Configuracion-Club — Gestión Centralizada de Configuración

**Change slug:** `configuracion-club`  
**Estado:** proposed  
**Épica:** EP-01 Acceso e Identidad  
**Fase:** Fase 1  

---

## Why

El sistema actualmente no tiene un lugar centralizado para almacenar la configuración de la instalación de pádel (nombre del club, horarios de operación, estado de pistas, credenciales de integraciones externas como Telegram o Redsys). Sin esto, cada capability (auth-otp-telegram, pagos-redsys, reservas) tendría que hardcodear o cargar desde variables de entorno, lo que no escala. 

Además, el estado operativo de la pista (ACTIVA/MANTENIMIENTO) debe ser configurable por el ADMIN sin tocar el código. Necesitamos un singleton `system_config` que actúe como fuente única de verdad para toda la configuración transversal del club.

---

## What Changes

- **Flyway V6** — Nueva tabla `system_config` (singleton):
  - `id` (PK, siempre = 1)
  - `club_name` VARCHAR
  - `club_description` TEXT
  - `pista_state` ENUM (ACTIVA / MANTENIMIENTO)
  - `payment_gateway` ENUM (CASH / REDSYS)
  - `telegram_bot_token` VARCHAR (cifrado con AES-256-GCM)
  - `redsys_merchant_id` VARCHAR (cifrado)
  - `redsys_merchant_key` VARCHAR (cifrado)
  - `max_participants_per_pista` INT
  - `updated_at` TIMESTAMPTZ
  - `updated_by_user_id` FK → users (nullable, para cambios del sistema)

- **Entidad JPA** `SystemConfig` — modelo para la tabla (con getters + auditabilidad)

- **Endpoints REST:**
  - `GET /api/admin/sistema/config` — ADMIN accede a la configuración (sin datos sensibles en claro)
  - `PATCH /api/admin/sistema/config` — ADMIN actualiza (requiere validación)
  - Encriptación/desencriptación automática en serialización/deserialización

- **EncryptionService** — Servicio de cifrado AES-256-GCM (usa ENCRYPTION_KEY del env)

---

## Capabilities

### New Capabilities

- `configuracion-club`: Gestión centralizada de configuración de la instalación (singleton `system_config`, endpoints REST, encriptación de secretos).
- `pistas`: Estado operativo de la pista única (ACTIVA/MANTENIMIENTO), configurable desde `system_config`.

### Modified Capabilities

_(Ninguna)_

---

## Impact

**Backend:**
- Nueva migración Flyway V6
- Nueva entidad `SystemConfig`
- Nuevo servicio `EncryptionService` (AES-256-GCM)
- Nuevo controlador `AdminConfigController`
- Nuevos DTOs para request/response

**Dependencias:**
- Desbloquea `disponibilidad-pistas` (Wave 2A) — necesita leer `max_participants_per_pista` y `pista_state`
- Desbloquea `auth-otp-telegram` (Wave 2B) — necesita leer `telegram_bot_token` cifrado
- Desbloquea `pagos-redsys` (Wave 4A) — necesita credenciales Redsys cifradas

**Seguridad:**
- ENCRYPTION_KEY debe ser env var sin fallback (D-CONF-01, opción A aprobada)
- Endpoints solo accesibles para ADMIN
- Secretos se cifran en BD, desencriptados en memoria

**Alcance fuera del change:**
- Interfaz web de edición (Fase 2 — `administracion-club`)
- Auditoría de cambios en config (puede añadirse a `audit_log` en el futuro)
- Validación de credenciales Redsys contra servidor real (mock en tests)
