# Capability: `notificaciones`

## Resumen
Gestión del envío de notificaciones automáticas por email y Telegram en respuesta a eventos
de negocio: confirmación de reserva, cancelación, recordatorio previo al partido y recibo de
pago. Es una capability interna disparada por los servicios de aplicación de `reservas`,
`pagos-redsys` y `autenticacion`; no expone ningún endpoint REST directo.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-TEL-03**: Solo las cuentas con `telegram_chat_id NOT NULL` reciben notificaciones vía Telegram; las demás solo reciben email.
- **RN-RGPD-04**: Los logs no deben contener contraseñas, tokens JWT, códigos OTP ni datos de tarjeta; los campos `recipient` y `message` de `notification_log` nunca incluyen esos datos.
- **RN-NOT-01**: Un fallo en el envío de Telegram (cuenta no vinculada, bot bloqueado, timeout de API) no bloquea el flujo principal de negocio; el sistema continúa y registra el fallo en `notification_log`.
- **RN-NOT-02**: Un fallo en el envío de email (SMTP caído, timeout) se registra en `notification_log` con `status=FAILED` y se reintenta en el siguiente ciclo del job de reintentos (máx. 3 intentos con backoff exponencial).
- **RN-NOT-03**: Cada notificación enviada genera un registro en `notification_log` con `status=SENT` o `status=FAILED`.

## Entidades implicadas
- **`notification_log`**: registro de cada intento de envío de notificación.
  - `id` (BIGSERIAL PK), `user_id` (FK nullable → `users`), `type` (EMAIL / TELEGRAM_DIRECT / TELEGRAM_GROUP), `recipient`, `subject` (solo email), `message`, `status` (PENDING / SENT / FAILED), `error_message`, `related_entity_type`, `related_entity_id`, `sent_at`, `created_at`.
- **`users`**: columna `telegram_chat_id` determina elegibilidad para notificaciones Telegram directas; `email` para notificaciones por correo.
- **`system_config`**: contiene `telegram_bot_token` (cifrado AES-256), `telegram_group_id`, `smtp_host`, `smtp_port`, `smtp_user`, `smtp_password` (cifrado AES-256).
- **`reservations`**: evento origen de notificaciones de confirmación, cancelación y recordatorio.
- **`payments`**: evento origen de notificaciones de recibo de pago.

## Endpoints
Sin endpoint REST directo — procesamiento interno/event-driven.
Las notificaciones se disparan desde `ReservaApplicationService` y `PagoApplicationService`
a través del puerto de salida `MensajeriaPort`. El job de recordatorios usa `@Scheduled`.

## Permisos
| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Disparar notificación (interno) | N/A — lógica de aplicación | N/A — lógica de aplicación | — |
| Consultar `notification_log` (futuro) | — | — | — |

> Nota: en v1.0 no existe endpoint de lectura de `notification_log`. El ADMIN accede a los
> registros directamente en BD para diagnóstico de fallos.

## Requirements

### Requirement 1: Notificación de confirmación de reserva *(email ✅ implementado — notificaciones-eventos-email; Telegram diferido a auth-otp-telegram)*
**El sistema DEBE enviar una notificación de confirmación cuando una reserva pase al estado `CONFIRMED`, incluyendo los datos de la pista, fecha, hora, duración e importe total. El sistema NO DEBE enviar la notificación si el titular está `INACTIVE` (dado de baja/anonimizado, RN-RGPD). El envío del email es asíncrono y post-commit; un fallo NO revierte ni bloquea la confirmación.**

#### Scenario 1: Reserva confirmada con Telegram vinculado — se envían email y Telegram
- **GIVEN** un usuario con `telegram_chat_id NOT NULL` y `status=ACTIVE` que acaba de confirmar una reserva
- **WHEN** `ReservaApplicationService` transiciona la reserva al estado `CONFIRMED`
- **THEN** el sistema envía un email de confirmación a `users.email` con los detalles de la reserva Y envía un mensaje directo por Telegram al `telegram_chat_id` del usuario, Y registra dos entradas en `notification_log` con `status=SENT`

#### Scenario 2: Reserva confirmada sin Telegram vinculado — solo email
- **GIVEN** un usuario con `telegram_chat_id IS NULL` que acaba de confirmar una reserva
- **WHEN** `ReservaApplicationService` transiciona la reserva al estado `CONFIRMED`
- **THEN** el sistema envía únicamente el email de confirmación, Y registra una entrada en `notification_log` con `type=EMAIL` y `status=SENT`, Y no se crea ningún intento de envío Telegram

#### Scenario: Titular inactivo no recibe notificación
- **GIVEN** una reserva cuyo titular está `INACTIVE` (dado de baja/anonimizado)
- **WHEN** la reserva pasa a `CONFIRMED`
- **THEN** el sistema NO envía ninguna notificación y no se crea entrada en `notification_log` para ese titular

### Requirement 2: Fallo en el envío de Telegram no bloquea el flujo principal
**El sistema DEBE continuar el flujo de negocio normal aunque el envío de la notificación por Telegram falle, registrando el error en `notification_log`.**

#### Scenario 3: Envío Telegram falla (bot bloqueado) — flujo principal continúa
- **GIVEN** un usuario con `telegram_chat_id NOT NULL` cuya reserva acaba de ser confirmada, Y el bot de Telegram está bloqueado por ese usuario
- **WHEN** `MensajeriaPort` intenta enviar el mensaje Telegram
- **THEN** la reserva permanece en estado `CONFIRMED`, Y se registra en `notification_log` con `type=TELEGRAM_DIRECT`, `status=FAILED`, `error_message` con la descripción del error de la API de Telegram, Y el email de confirmación se envía igualmente si el SMTP está disponible

#### Scenario 4: Telegram API devuelve timeout — flujo principal no se bloquea
- **GIVEN** una reserva recién confirmada con usuario que tiene `telegram_chat_id NOT NULL`
- **WHEN** la llamada a la API de Telegram supera el timeout configurado (10 s lectura)
- **THEN** la transacción de negocio (reserva `CONFIRMED`) ya está commiteada, Y la notificación Telegram se registra en `notification_log` con `status=FAILED`, Y el sistema no lanza excepción no controlada al hilo principal

### Requirement 3: Fallo SMTP — registro en `notification_log` y reintento *(✅ implementado — notificaciones-eventos-email)*
**El sistema DEBE registrar en `notification_log` con `status=FAILED` cualquier fallo en el envío de email, y reintentarlo automáticamente (job `@Scheduled`) hasta 3 veces con backoff; tras el tercer intento fallido la entrada permanece `FAILED` sin más reintentos. Cada intento de envío deja una entrada en `notification_log`, y ni `message` ni `recipient` contienen contraseñas, tokens, OTP ni datos de tarjeta (RN-RGPD-04).**

#### Scenario 5: SMTP caído — notificación pasa a FAILED y se reintenta
- **GIVEN** una reserva recién confirmada Y el servidor SMTP está caído
- **WHEN** `MensajeriaPort` intenta enviar el email de confirmación
- **THEN** se crea una entrada en `notification_log` con `status=FAILED` y `error_message` describiendo el error SMTP, Y el job de reintentos (`@Scheduled`) detecta la entrada FAILED y reintenta el envío en el siguiente ciclo (máx. 3 intentos), Y si el tercer intento falla, `status` permanece `FAILED` sin más reintentos automáticos

### Requirement 4: Notificación de cancelación de reserva *(email ✅ implementado — notificaciones-eventos-email; Telegram diferido a auth-otp-telegram)*
**El sistema DEBE enviar una notificación de cancelación cuando una reserva pase al estado `CANCELLED`, indicando el motivo de cancelación si está disponible. El sistema NO DEBE enviar la notificación si el titular está `INACTIVE`.**

#### Scenario 6: Reserva cancelada — notificación enviada al titular
- **GIVEN** una reserva en estado `CONFIRMED` cuyo titular la cancela mediante `DELETE /api/reservas/{id}`
- **WHEN** `ReservaApplicationService` transiciona la reserva al estado `CANCELLED`
- **THEN** el sistema envía notificación de cancelación al titular (email siempre, Telegram si `telegram_chat_id NOT NULL`), Y registra las entradas correspondientes en `notification_log`, Y no se envían notificaciones a los participantes que no son titulares en v1.0

#### Scenario: Titular inactivo no recibe cancelación
- **GIVEN** una reserva cuyo titular está `INACTIVE`
- **WHEN** la reserva pasa a `CANCELLED`
- **THEN** el sistema NO envía ninguna notificación y no se crea entrada en `notification_log` para ese titular

### Requirement 5: Notificación de recibo de pago confirmado *(email ✅ implementado — notificaciones-eventos-email; Telegram diferido a auth-otp-telegram)*
**El sistema DEBE enviar un recibo de pago al titular de la reserva cuando el estado de `payments` pase a `PAID` (por webhook Redsys o por registro de efectivo del ADMIN), con importe, fecha y referencia. El sistema NO DEBE enviar la notificación si el titular está `INACTIVE`.**

#### Scenario 7: Pago confirmado vía webhook Redsys — recibo enviado
- **GIVEN** un pago en estado `IN_PROGRESS` cuyo webhook Redsys válido llega al backend confirmando el pago (Ds_Response en rango 0000-0099)
- **WHEN** `PagoApplicationService` transiciona el pago a `PAID`
- **THEN** el sistema envía un recibo de pago al email del titular con importe, fecha y referencia Redsys, Y si el titular tiene `telegram_chat_id NOT NULL` también recibe confirmación por Telegram, Y se registran las entradas correspondientes en `notification_log`

#### Scenario: Titular inactivo no recibe recibo
- **GIVEN** un pago cuyo titular está `INACTIVE`
- **WHEN** el pago pasa a `PAID`
- **THEN** el sistema NO envía ninguna notificación y no se crea entrada en `notification_log` para ese titular

### Requirement 6: Notificación al grupo de Telegram tras confirmar reserva
**El sistema DEBE publicar un mensaje en el grupo de Telegram configurado en `system_config.telegram_group_id` cuando una reserva pase a `CONFIRMED`, indicando la fecha, hora y plazas disponibles para que otros jugadores puedan unirse.**

#### Scenario 8: Reserva confirmada — mensaje publicado en el grupo
- **GIVEN** una reserva recién confirmada Y `system_config.telegram_group_id NOT NULL`
- **WHEN** `ReservaApplicationService` completa la transición a `CONFIRMED`
- **THEN** el bot publica un mensaje en el grupo con los datos de la reserva y las plazas libres, Y el `reservations.telegram_message_id` se actualiza con el ID del mensaje publicado, Y se registra en `notification_log` con `type=TELEGRAM_GROUP` y `status=SENT`

### Requirement 7: Envío de emails transaccionales por SMTP *(✅ implementado — usuarios-alta-edicion-email)*

**El sistema DEBE poder enviar emails transaccionales a través de un proveedor SMTP configurable por entorno (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`). Las credenciales NO deben estar en el repositorio (solo placeholders en `.env.example`). Implementado con puerto de dominio `NotificationPort` + adaptador `JavaMailSender` asíncrono; con `MAIL_HOST` vacío el envío falla de forma tolerante.**

#### Scenario: Configuración SMTP presente
- **WHEN** el sistema necesita enviar un email y las variables `MAIL_*` están definidas
- **THEN** lo envía por el servidor SMTP configurado con remitente `MAIL_FROM`

#### Scenario: Credenciales fuera del control de versiones
- **WHEN** se inspecciona el repositorio
- **THEN** no hay credenciales SMTP reales commiteadas

### Requirement 8: Email de bienvenida al activar una cuenta *(✅ implementado — usuarios-alta-edicion-email)*

**El sistema DEBE enviar un email de bienvenida cuando una cuenta se activa: en el alta directa por el administrador incluye la contraseña temporal generada; en la aprobación de una cuenta pendiente da la bienvenida SIN contraseña (el usuario conserva la suya). El envío es asíncrono, NO bloquea ni revierte la activación si falla, y ninguna contraseña se registra en logs (RN-RGPD-04).**

#### Scenario: Bienvenida tras el alta directa (con contraseña)
- **WHEN** el administrador da de alta un usuario que queda `ACTIVE`
- **THEN** el usuario recibe un email de bienvenida con la contraseña temporal generada por el sistema

#### Scenario: Bienvenida tras aprobar una cuenta pendiente (sin contraseña)
- **WHEN** el administrador aprueba una cuenta `PENDING`
- **THEN** el usuario recibe un email de bienvenida indicándole que su cuenta está aprobada, sin contraseña

#### Scenario: Un fallo de envío no rompe la activación
- **GIVEN** el servidor SMTP no está disponible
- **WHEN** el administrador activa una cuenta
- **THEN** la cuenta queda activada correctamente y el fallo se registra sin exponer contraseñas ni propagar error

## Casos límite
- Si `system_config.telegram_bot_token IS NULL`, no se intentan envíos Telegram; solo email.
- Si `system_config.telegram_group_id IS NULL`, no se publica en el grupo; solo mensajes directos.
- Si tanto SMTP como Telegram fallan simultáneamente, ambos quedan registrados en `notification_log` con `status=FAILED`; el flujo de negocio no se revierte.
- Un usuario anonimizado (RGPD) no recibe notificaciones porque `email` y `telegram_chat_id` han sido nullificados; el sistema omite el envío sin error.
- El campo `message` de `notification_log` no debe contener códigos OTP, tokens JWT ni contraseñas (RN-RGPD-04).
- El job de recordatorios no debe disparar notificaciones para reservas en estado `CANCELLED` o `COMPLETED`.

## Dependencias con otras capabilities
- **`reservas`**: los eventos `RESERVATION_CREATED` y `RESERVATION_CANCELLED` disparan notificaciones.
- **`pagos-redsys`**: el evento `PAYMENT_CONFIRMED` dispara el recibo de pago.
- **`autenticacion`**: el flujo de reset de contraseña usa `MensajeriaPort` para enviar el OTP por Telegram.
- **`auditoria`**: los fallos de envío Telegram con secret inválido generan entradas en `audit_log` con `action=TELEGRAM_WEBHOOK_INVALID_SECRET`.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 10 | Pago confirmado | Mobile | USER | [`12-pago-confirmado.html`](../../../docs/ux/mockups/12-pago-confirmado.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de reserva de pista con pago Redsys** — la pantalla de pago confirmado (pantalla 10) incluye el aviso de que el usuario recibirá una notificación por Telegram si tiene la cuenta vinculada.

### Notas de UX

> - La pantalla de pago confirmado (pantalla 10) debe indicar que se ha enviado una notificación por Telegram solo si el usuario tiene `telegram_chat_id` vinculado (RN-TEL-03); si no está vinculado, omitir el aviso.
> - Un fallo en el envío de notificación no debe impedir que la pantalla de confirmación se muestre al usuario; el flujo principal no se bloquea (RN-NOT-01).
