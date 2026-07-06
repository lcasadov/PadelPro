## Why

La capability `notificaciones` está parcial: el envío SMTP y el email de bienvenida (Requirements 7-8) ✅ existen (`NotificationPort` + `SmtpNotificationAdapter`), pero los **emails de eventos de negocio** (confirmación de reserva, cancelación, recibo de pago) no están implementados, no hay tabla `notification_log` para registrar envíos, ni job de reintentos. Hoy un jugador reserva/paga y no recibe ningún aviso. Es lo que resta de Wave 4B.

Las patas **Telegram** de la spec (Req 2, Req 6 grupo, y el envío directo Telegram de Req 1/4/5) dependen de `auth-otp-telegram` (vinculación de `telegram_chat_id`, bot), que **no está construido** → se difieren. Este change entrega la parte **email**, que es implementable ya sobre la infra existente.

## What Changes

- **[Backend] `notification_log`** (migración Flyway nueva): registra cada intento de envío (`type=EMAIL`, `recipient`, `subject`, `message`, `status` PENDING/SENT/FAILED, `error_message`, `related_entity_type/id`, `sent_at`, `created_at`) — RN-NOT-03.
- **[Backend] Email de confirmación de reserva** (Req 1, pata email): al pasar una reserva a `CONFIRMED`, se envía email al titular con pista/fecha/hora/duración/importe y se registra en `notification_log`.
- **[Backend] Email de cancelación** (Req 4, pata email): al pasar una reserva a `CANCELLED`, email al titular con el motivo si está disponible.
- **[Backend] Email de recibo de pago** (Req 5, pata email): al pasar un `Payment` a `PAID` (webhook Redsys o efectivo ADMIN), recibo al titular con importe, fecha y referencia.
- **[Backend] Envío asíncrono y tolerante a fallos** (RN-NOT-01/02): un fallo de envío NO revierte ni bloquea la transacción de negocio; se registra `status=FAILED`.
- **[Backend] Job de reintentos** (Req 3, `@Scheduled`): reintenta los emails `FAILED` hasta 3 veces con backoff; tras el 3º intento fallido queda `FAILED` sin más reintentos.
- **Emails transaccionales** reutilizan el `NotificationPort`/`SmtpNotificationAdapter` y el patrón de plantillas existente (`WelcomeEmailTemplate`).

## Capabilities

### New Capabilities
<!-- Ninguna nueva: se completa `notificaciones`. -->

### Modified Capabilities
- `notificaciones`: se implementan las patas **email** de los Requirements 1, 4 y 5 y el Requirement 3 (registro en `notification_log` + reintentos). Las patas Telegram (Req 2, 6, y el envío Telegram de 1/4/5) quedan explícitamente diferidas.
- `reservas` / `pagos-redsys`: se enganchan disparadores de notificación en los puntos de transición existentes (confirmar/cancelar reserva; pago a PAID), sin cambiar su lógica de dominio.

## Impact

- **Fase del producto**: fase-1 (`notificaciones` es fase-1, Wave 4B).
- **Backend**: migración `notification_log`; entidad/puerto/adaptador de registro; plantillas de email (confirmación, cancelación, recibo); enganches en `AdminReservaService`/`CancelarReservaService` (transiciones de reserva) y en `ProcesarWebhookService`/`RegistrarPagoEfectivoService` (pago PAID); job `@Scheduled` de reintentos; envío `@Async`. Reutiliza `NotificationPort`/`SmtpNotificationAdapter`.
- **Datos / migraciones**: una migración Flyway (`notification_log`). Sin cambios en `reservations`/`payments`.
- **Config**: reutiliza `MAIL_*` (SMTP ya configurable). Sin secretos nuevos.
- **Sin endpoints REST** (event-driven, RN de la spec). Sin impacto en frontend (opcional: la pantalla de pago confirmado ya menciona el aviso).

## Fuera de alcance

- **Todo Telegram**: mensaje directo (Req 1/4/5 pata Telegram), tolerancia a fallo Telegram (Req 2), publicación en el grupo (Req 6) — dependen de `auth-otp-telegram` (bot + `telegram_chat_id`), no construido. Se difieren a cuando esa capability exista.
- **Recordatorio previo al partido** (mencionado en el resumen de la spec) — requiere un job de recordatorios adicional; se puede añadir después.
- **Endpoint de lectura de `notification_log`** — en v1 el ADMIN consulta en BD (per spec); no se expone API.
- **Notificar a participantes no-titulares** — la spec v1 solo notifica al titular.
