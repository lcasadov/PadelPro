# Capability: configuracion-club

## Purpose

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

**El sistema DEBE (MUST) devolver la configuración global del club al ADMIN, omitiendo los valores en claro de los campos sensibles cifrados. El ADMIN accede mediante un punto único de configuración (singleton system_config) con encriptación AES-256-GCM para secretos.**

#### Scenario: ADMIN lee la configuración del sistema

- **GIVEN** un administrador autenticado con `role=ADMIN`
- **AND** existe la fila `system_config` con `id=1`
- **WHEN** se envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene `pricePerHour`, `cancellationDeadlineHours`, `maxParticipants`, `paymentGateway`, `telegramGroupId`, `smtpHost`, `smtpPort`, `smtpUser`
- **AND** los campos `redsysSecretKey`, `telegramBotToken` y `smtpPassword` no aparecen en el cuerpo o aparecen enmascarados (ej. `"****"`) (RN-SEC-02)

#### Scenario: ADMIN consulta la configuración actual (sistema centralizado)

- **GIVEN** un usuario autenticado con rol ADMIN
- **AND** la tabla system_config existe con datos iniciales
- **WHEN** envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con HTTP `200`
- **AND** la respuesta contiene:
  - `clubName`: nombre del club
  - `clubDescription`: descripción
  - `pistaState`: ACTIVA o MANTENIMIENTO
  - `paymentGateway`: CASH o REDSYS
  - `maxParticipantsPerPista`: número máximo de participantes
  - `telegramBotConfigured`: true/false (nunca exponer el token)
  - `redsysConfigured`: true/false (nunca exponer credenciales)
  - `updatedAt`: cuándo fue actualizado por última vez

#### Scenario: USER intenta acceder a la configuración

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con código `403` (ACCESS_DENIED)

#### Scenario: Request no autenticado recibe 401

- **GIVEN** sin autenticación
- **WHEN** se envía `GET /api/admin/sistema/config` sin cabecera `Authorization`
- **THEN** el sistema responde con HTTP `401` (AUTH_REQUIRED)

---

### Requirement 2: Actualización de configuración del sistema

**El sistema DEBE (MUST) permitir al ADMIN actualizar uno o más parámetros de la configuración global con semántica PATCH. Solo los campos incluidos en el cuerpo se actualizan. Requiere validación de credenciales para pasarelas de pago y encriptación AES-256-GCM de secretos.**

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

#### Scenario: ADMIN actualiza el plazo de cancelación

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con body `{ "cancellationDeadlineHours": 24 }`
- **THEN** el sistema responde con código `200`
- **AND** `system_config.cancellation_deadline_hours = 24` en base de datos

#### Scenario: Valores inválidos son rechazados

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con `maxParticipants = 0` (o negativo), `pricePerHour <= 0`, o `cancellationDeadlineHours` negativo
- **THEN** el sistema responde con código `400`
- **AND** el cuerpo sigue el esquema `ErrorResponse` indicando qué campo es inválido

#### Scenario: Actualización de secretos cifrados (RN-SEC-02)

- **GIVEN** un administrador autenticado
- **WHEN** se envía `PATCH /api/admin/sistema/config` con body `{ "redsysSecretKey": "nuevo-valor" }`
- **THEN** el sistema responde con código `200`
- **AND** el valor `nuevo-valor` se cifra con AES-256-GCM antes de persistir en base de datos
- **AND** la respuesta devuelve el campo enmascarado (ej. `"****"`), nunca en claro

#### Scenario: ADMIN actualiza la configuración (payment gateway a REDSYS)

- **GIVEN** un usuario autenticado con rol ADMIN
- **AND** la configuración actual tiene `paymentGateway=CASH`
- **WHEN** envía `PATCH /api/admin/sistema/config` con `paymentGateway=REDSYS`, credenciales válidas
- **THEN** el sistema valida que todos los campos Redsys estén presentes
- **AND** cifra los secretos con AES-256-GCM antes de guardar
- **AND** responde con HTTP `200` sin exponer secretos

#### Scenario: ADMIN intenta cambiar a REDSYS sin credenciales (error)

- **GIVEN** un usuario autenticado con rol ADMIN
- **WHEN** envía `PATCH /api/admin/sistema/config` con `paymentGateway=REDSYS` pero `redsysMerchantKey` vacío
- **THEN** el sistema responde con HTTP `400`
- **AND** cuerpo contiene código error `VALIDATION_ERROR`

#### Scenario: Secretos se cifran en BD y desencriptan automáticamente

- **GIVEN** ENCRYPTION_KEY está configurada en el ambiente
- **WHEN** el ADMIN actualiza `telegram_bot_token` y guarda vía PATCH
- **THEN** en la BD, el valor se almacena cifrado con AES-256-GCM + IV aleatorio
- **AND** cuando internamente el sistema necesita usar el token, lo desencripta en memoria
- **AND** nunca aparece en plaintext en responses HTTP

#### Scenario: USER intenta actualizar la configuración

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** envía `PATCH /api/admin/sistema/config`
- **THEN** el sistema responde con código `403` (ACCESS_DENIED)

#### Scenario: Request no autenticado intenta actualizar

- **GIVEN** sin autenticación
- **WHEN** se envía `PATCH /api/admin/sistema/config` sin cabecera `Authorization`
- **THEN** el sistema responde con HTTP `401` (AUTH_REQUIRED)

---

### Requirement 3: Integridad del singleton de configuración

**El sistema DEBE (MUST) garantizar que siempre existe exactamente una fila en `system_config` (id=1). No se permiten operaciones de creación ni eliminación de la configuración.**

#### Scenario: La configuración siempre existe (inicialización)

- **GIVEN** el sistema se inicializa por primera vez (migración Flyway)
- **WHEN** se ejecuta la migración inicial de datos
- **THEN** la tabla `system_config` contiene exactamente una fila con `id=1` y valores por defecto sensatos
- **AND** el constraint `CHECK (id = 1)` de la tabla impide insertar filas adicionales

---

### Requirement 4: Los logs de configuración no contienen secretos

**El sistema DEBE (MUST) garantizar que ningún log, metric, o error message contiene valores de secretos en plaintext.**

#### Scenario: Cambio de configuración se audita sin exponer secreto

- **GIVEN** el sistema tiene auditoría habilitada
- **WHEN** el ADMIN actualiza `telegram_bot_token` vía `PATCH /api/admin/sistema/config`
- **THEN** se inserta una fila en `audit_log` con `action='CONFIG_UPDATED'`
- **AND** el campo `details` usa mascarado (`***REDACTED***`) en lugar de valores reales

---

### Requirement: Interfaz de administración de la configuración

**El panel de administración DEBE (MUST) ofrecer una pantalla, accesible solo a usuarios ADMIN, para leer y actualizar la configuración global del club, consumiendo los endpoints `GET`/`PATCH /api/admin/sistema/config`, respetando el enmascarado de secretos y la semántica de actualización parcial.**

#### Scenario: ADMIN abre la pantalla de configuración

- **GIVEN** un usuario autenticado con `role=ADMIN`
- **WHEN** navega a `/admin/config`
- **THEN** la pantalla carga la configuración actual vía `GET /api/admin/sistema/config`
- **AND** muestra el precio por hora, el estado de la pista, el aforo máximo, el plazo de cancelación y la pasarela de pago con sus valores actuales
- **AND** los campos de secreto (bot token de Telegram, webhook secret, claves Redsys) se muestran enmascarados, nunca en claro

#### Scenario: ADMIN actualiza el precio por hora desde la UI

- **GIVEN** un ADMIN en `/admin/config`
- **WHEN** cambia el precio por hora a `12.50` y guarda
- **THEN** la pantalla envía `PATCH /api/admin/sistema/config` con `{ "pricePerHour": 12.50 }`
- **AND** al responder `200` muestra confirmación y el nuevo valor persiste al recargar

#### Scenario: Dejar un secreto en blanco conserva el valor almacenado

- **GIVEN** un ADMIN en `/admin/config` con un bot token de Telegram ya configurado (mostrado enmascarado)
- **WHEN** modifica otro campo (p.ej. el precio) y guarda sin teclear un nuevo bot token
- **THEN** el `PATCH` NO incluye `telegramBotToken`
- **AND** el bot token almacenado permanece intacto

#### Scenario: ADMIN configura las credenciales de Telegram

- **GIVEN** un ADMIN en `/admin/config`
- **WHEN** introduce un bot token y un webhook secret nuevos y guarda
- **THEN** el `PATCH` incluye `telegramBotToken` y `telegramWebhookSecret`
- **AND** el backend los cifra (AES-256-GCM) y la respuesta los devuelve enmascarados

#### Scenario: Un USER no puede acceder a la pantalla de configuración

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** intenta navegar a `/admin/config`
- **THEN** el cliente le deniega/redirige el acceso
- **AND** cualquier llamada a `PATCH /api/admin/sistema/config` responde `403`

#### Scenario: Valores inválidos se avisan en cliente y el backend los rechaza

- **GIVEN** un ADMIN en `/admin/config`
- **WHEN** introduce `pricePerHour <= 0` o `maxParticipants < 1`
- **THEN** la UI marca el campo como inválido y no permite guardar
- **AND** si aun así se enviara, el backend responde `400` con `ErrorResponse` indicando el campo

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

## Mockups asociados

Esta capability es **transversal o puramente backend**. No tiene pantallas de usuario directas en el sistema actual.

Está implícita en los siguientes mockups donde el concepto aparece de forma indirecta:

- **`configuracion-club`**: los valores de `system_config` (`max_participants`, horario de apertura, `price_per_hour`) condicionan la disponibilidad mostrada en `07 · Buscar disponibilidad` → [`03-buscar-disponibilidad.html`](../../../docs/ux/mockups/03-buscar-disponibilidad.html) y el importe mostrado en `08 · Confirmar reserva` → [`04-confirmar-reserva.html`](../../../docs/ux/mockups/04-confirmar-reserva.html). El estado de mantenimiento de la pista (también en `system_config`) vacía el resultado de disponibilidad en esas mismas pantallas. Los parámetros de cancelación (`cancellation_deadline_hours`) determinan si el botón de cancelación está activo en `12 · Detalle de reserva` → [`13-detalle-reserva.html`](../../../docs/ux/mockups/13-detalle-reserva.html).

Si se evoluciona esta capability hacia una UI dedicada de configuración para el ADMIN, añadir el flujo correspondiente en [`docs/ux/flujos.md`](../../../docs/ux/flujos.md) antes de generar mockups.
