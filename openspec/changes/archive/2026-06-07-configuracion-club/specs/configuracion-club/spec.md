## ADDED Requirements

### [AÑADIDO] Requirement: Gestión centralizada de configuración del club

**El sistema DEBE proporcionar un punto único de configuración (singleton `system_config`) accesible solo a ADMIN mediante endpoints REST, con encriptación AES-256-GCM para secretos.**

#### Scenario: ADMIN consulta la configuración actual

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

#### Scenario: USER no puede acceder a la configuración

- **GIVEN** un usuario autenticado con rol USER
- **WHEN** envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde con HTTP `403`
- **AND** cuerpo contiene código error `ACCESS_DENIED`

#### Scenario: Request no autenticado recibe 401

- **GIVEN** sin autenticación
- **WHEN** se envía `GET /api/admin/sistema/config` sin cabecera `Authorization`
- **THEN** el sistema responde con HTTP `401`
- **AND** cuerpo contiene código error `AUTH_REQUIRED`

#### Scenario: Secretos se cifran en BD y desencriptan automáticamente

- **GIVEN** ENCRYPTION_KEY está configurada en el ambiente
- **WHEN** el ADMIN actualiza `telegram_bot_token` y guarda vía PATCH
- **THEN** en la BD, el valor se almacena cifrado con AES-256-GCM + IV aleatorio
- **AND** cuando internamente el sistema necesita usar el token, lo desencripta en memoria
- **AND** nunca aparece en plaintext en responses HTTP

---

### [AÑADIDO] Requirement: Los logs de configuración no contienen secretos

**El sistema DEBE garantizar que ningún log, metric, o error message contiene valores de secretos.**

#### Scenario: Cambio de configuración se audita sin exponer secreto

- **GIVEN** el sistema tiene auditoría habilitada
- **WHEN** el ADMIN actualiza `telegram_bot_token` vía `PATCH /api/admin/sistema/config`
- **THEN** se inserta una fila en `audit_log` con `action='CONFIG_UPDATED'`
- **AND** el campo `details` usa mascarado (`***REDACTED***`) en lugar de valores reales
