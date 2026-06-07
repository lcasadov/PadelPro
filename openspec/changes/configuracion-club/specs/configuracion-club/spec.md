## NEW Requirements

### Requirement: Gestión centralizada de configuración del club

**El sistema DEBE proporcionar un punto único de configuración (singleton `system_config`) accesible solo a ADMIN mediante endpoints REST, con encriptación AES-256-GCM para secretos.**

#### Scenario: ADMIN consulta la configuración actual

- **WHEN** un ADMIN autenticado envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde `200` con un objeto que contiene:
  - `clubName`: nombre del club
  - `clubDescription`: descripción
  - `pistaState`: ACTIVA o MANTENIMIENTO
  - `paymentGateway`: CASH o REDSYS
  - `maxParticipantsPerPista`: número máximo de participantes
  - `telegramBotConfigured`: true/false (nunca exponer el token)
  - `redsysConfigured`: true/false (nunca exponer credenciales)
  - `updatedAt`: cuándo fue actualizado por última vez

#### Scenario: ADMIN actualiza la configuración (payment gateway a REDSYS)

- **WHEN** un ADMIN autenticado envía `PATCH /api/admin/sistema/config` con `paymentGateway=REDSYS`, `redsysMerchantId=...`, `redsysMerchantKey=...`
- **THEN** el sistema valida que todos los campos Redsys estén presentes y no-empty
- **AND** guarda la configuración, cifrando automáticamente los secretos con AES-256-GCM
- **AND** responde `200` con la configuración actualizada (sin exponer secretos)

#### Scenario: ADMIN intenta cambiar a REDSYS sin credenciales (error)

- **WHEN** un ADMIN intenta `PATCH /api/admin/sistema/config` con `paymentGateway=REDSYS` pero `redsysMerchantKey` vacío
- **THEN** el sistema responde `400` con `{ "error": "VALIDATION_ERROR", "message": "REDSYS payment_gateway requires redsys_merchant_id and redsys_merchant_key" }`

#### Scenario: USER no puede acceder a la configuración

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde `403` con `{ "error": "ACCESS_DENIED" }`

#### Scenario: Request no autenticado recibe 401

- **WHEN** se envía `GET /api/admin/sistema/config` sin cabecera `Authorization`
- **THEN** el sistema responde `401` con `{ "error": "AUTH_REQUIRED" }`

#### Scenario: Secretos se cifran en BD y desencriptan automáticamente

- **WHEN** el ADMIN actualiza `telegram_bot_token` a `123:ABC_xyz...` y guarda
- **THEN** en la BD, el valor se almacena como ciphertext+IV cifrado con AES-256-GCM(ENCRYPTION_KEY)
- **AND** cuando el sistema necesita usar el token (ej. enviar mensaje a Telegram), lo desencripta automáticamente en memoria
- **AND** nunca se expone el token en plaintext en responses HTTP

---

### Requirement: Los logs de configuración no contienen secretos

**El sistema DEBE garantizar que ningún log, metric, o error message contiene valores de secretos.**

#### Scenario: Cambio de configuración se audita sin exponer secreto

- **WHEN** el ADMIN actualiza `telegram_bot_token` vía `PATCH /api/admin/sistema/config`
- **THEN** se inserta una fila en `audit_log` con `action='CONFIG_UPDATED'`, `entity_type='SYSTEM_CONFIG'`
- **AND** el campo `details` NO contiene el token en claro, solo `{"updatedFields": ["telegram_bot_token"], "oldValue": "***REDACTED***", "newValue": "***REDACTED***"}`
