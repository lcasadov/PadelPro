# Capability: configuracion-club

## Resumen

Gestiona los parámetros globales del club almacenados en la tabla `system_config` (singleton, `id=1`). Incluye precio por hora, horario de apertura y cierre, máximo de participantes por reserva, política de cancelación, divisa, zona horaria y configuración de los servicios externos (Redsys, Telegram, SMTP). Los campos sensibles (`redsys_secret_key`, `telegram_bot_token`, `smtp_password`) se cifran con AES-256-GCM y nunca se devuelven en claro en las respuestas de la API (RN-SEC-02).

## Fase

Fase 1

## Reglas de negocio implicadas

- **RN-RES-02**: El máximo de participantes por reserva lo determina `system_config.max_participants` (por defecto 4). Ninguna reserva puede superar este valor.
- **RN-RES-04**: La ventana de reserva anticipada y la política de cancelación provienen de `system_config.cancellation_deadline_hours`.
- **RN-SEC-02**: Los secretos del sistema (`redsys_secret_key`, `telegram_bot_token`, `smtp_password`) se cifran con AES-256-GCM en base de datos. Las respuestas de la API nunca devuelven estos campos en claro.

## Entidades implicadas

**system_config** (tabla `system_config`, singleton `id=1`):
- `id` (siempre 1, CHECK id=1)
- `price_per_hour` (`NUMERIC(12,2)`) — precio base por hora de pista
- `cancellation_deadline_hours` (`INTEGER`) — horas mínimas de antelación para cancelar sin coste
- `max_participants` (`INTEGER`) — máximo de jugadores por reserva (por defecto 4)
- `payment_gateway` (`payment_gateway ENUM`) — pasarela activa (ej. `REDSYS`)
- `redsys_merchant_code` (`VARCHAR(50)`) — código de comercio Redsys
- `redsys_terminal` (`VARCHAR(5)`) — terminal Redsys
- `redsys_secret_key` (`VARCHAR(255)`) — cifrado AES-256-GCM
- `telegram_bot_token` (`VARCHAR(255)`) — cifrado AES-256-GCM
- `telegram_group_id` (`VARCHAR(50)`) — ID del grupo de Telegram donde se publican reservas
- `smtp_host`, `smtp_port`, `smtp_user` — configuración SMTP
- `smtp_password` (`VARCHAR(255)`) — cifrado AES-256-GCM
- `updated_by_id` (FK → `users.id`) — último ADMIN que actualizó la config
- `updated_at` (`TIMESTAMPTZ`)

## Endpoints

| Método | Path | OperationId | Autenticación |
|---|---|---|---|
| `GET` | `/api/admin/sistema/config` | `getSystemConfig` | JWT requerido (ADMIN) |
| `PATCH` | `/api/admin/sistema/config` | `actualizarSystemConfig` | JWT requerido (ADMIN) |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| `GET /api/admin/sistema/config` | Permitido | Denegado (403) | Denegado (401) |
| `PATCH /api/admin/sistema/config` | Permitido | Denegado (403) | Denegado (401) |

## Requirements

### Requirement 1: Lectura de configuración del sistema

**El sistema DEBE devolver la configuración global del club al ADMIN, omitiendo los valores en claro de los campos sensibles cifrados.**

#### Scenario: ADMIN lee la configuración del sistema

- **GIVEN** un administrador autenticado con `role=ADMIN`
- **AND** existe la fila `system_config` con `id=1`
- **WHEN** se envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene `pricePerHour`, `cancellationDeadlineHours`, `maxParticipants`, `paymentGateway`, `telegramGroupId`, `smtpHost`, `smtpPort`, `smtpUser`
- **AND** los campos `redsysSecretKey`, `telegramBotToken` y `smtpPassword` no aparecen en el cuerpo o aparecen enmascarados (ej. `"****"`) (RN-SEC-02)

#### Scenario: USER intenta acceder a la configuración

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con código `403`

---

### Requirement 2: Actualización de configuración del sistema

**El sistema DEBE permitir al ADMIN actualizar uno o más parámetros de la configuración global con semántica PATCH. Solo los campos incluidos en el cuerpo se actualizan.**

#### Scenario: ADMIN actualiza el máximo de participantes

- **GIVEN** un administrador autenticado con `role=ADMIN`
- **AND** `system_config.max_participants = 4`
- **WHEN** se envía `PATCH /api/admin/sistema/config` con body `{ "maxParticipants": 3 }`
- **THEN** el sistema responde con código `200`
- **AND** `system_config.max_participants = 3` en base de datos
- **AND** `updated_by_id` se actualiza con el `id` del ADMIN autenticado
- **AND** `updated_at` se actualiza al momento actual
- **AND** la acción `CONFIG_UPDATED` se registra en `audit_log`

#### Scenario: ADMIN actualiza el precio por hora

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con body `{ "pricePerHour": 12.50 }`
- **THEN** el sistema responde con código `200`
- **AND** `system_config.price_per_hour = 12.50` en base de datos
- **AND** el precio nuevo se aplica a todas las reservas futuras (no retroactivamente a reservas existentes)

#### Scenario: Valores inválidos son rechazados

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con `maxParticipants = 0` o `maxParticipants` negativo
- **THEN** el sistema responde con código `400`
- **AND** el cuerpo sigue el esquema `ErrorResponse` indicando qué campo es inválido

#### Scenario: Actualización de secretos cifrados (RN-SEC-02)

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con body `{ "redsysSecretKey": "nuevo-valor" }`
- **THEN** el sistema responde con código `200`
- **AND** el valor `nuevo-valor` se cifra con AES-256-GCM antes de persistir en base de datos
- **AND** la respuesta devuelve el campo enmascarado (ej. `"****"`), nunca en claro

---

### Requirement 3: Integridad del singleton de configuración

**El sistema DEBE garantizar que siempre existe exactamente una fila en `system_config` (id=1). No se permiten operaciones de creación ni eliminación de la configuración.**

#### Scenario: La configuración siempre existe (inicialización)

- **GIVEN** el sistema se inicializa por primera vez (migración Flyway)
- **WHEN** se ejecuta la migración inicial de datos
- **THEN** la tabla `system_config` contiene exactamente una fila con `id=1` y valores por defecto sensatos
- **AND** el constraint `CHECK (id = 1)` de la tabla impide insertar filas adicionales

---

## Casos límite

- La primera lectura de `system_config` en un backend recién desplegado no debe fallar aunque los campos de Redsys y Telegram estén vacíos. El sistema debe indicar en la respuesta qué integraciones están configuradas y cuáles no.
- Si `max_participants` se reduce (ej. de 4 a 3), las reservas existentes con 4 participantes no se ven afectadas retroactivamente.
- El campo `price_per_hour` solo se usa para calcular el importe de nuevas reservas. Las reservas ya creadas conservan el precio calculado en el momento de su creación.
- Los campos sensibles cifrados se descifran en memoria solo cuando son necesarios (para construir la firma Redsys o para el cliente SMTP), nunca se exponen en ningún log ni en ninguna respuesta HTTP.

## Dependencias con otras capabilities

- **`reservas`**: la capability `reservas` consulta `system_config.max_participants` al crear una reserva y `system_config.cancellation_deadline_hours` al cancelarla (RN-RES-02, RN-RES-04).
- **`pagos-redsys`**: la capability `pagos-redsys` usa `system_config.redsys_merchant_code`, `redsys_terminal` y `redsys_secret_key` (descifrada) para construir la firma HMAC de Redsys.
- **`auth-otp-telegram`**: el webhook de Telegram se valida usando `system_config.telegram_bot_token` (descifrado).
- **`notificaciones`**: las notificaciones por email usan la configuración SMTP de `system_config`.
- **`auditoria`**: toda actualización de `system_config` genera una entrada en `audit_log`.
- **`roles-permisos`**: ambos endpoints de esta capability requieren `role=ADMIN`.
