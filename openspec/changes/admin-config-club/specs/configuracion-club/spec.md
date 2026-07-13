## ADDED Requirements

### Requirement: Interfaz de administración de la configuración

**El panel de administración DEBE ofrecer una pantalla, accesible solo a usuarios ADMIN, para leer y actualizar la configuración global del club, consumiendo los endpoints `GET`/`PATCH /api/admin/sistema/config`, respetando el enmascarado de secretos y la semántica de actualización parcial.**

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
