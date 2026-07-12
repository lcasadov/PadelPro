## Why

La capability `notificaciones` está **parcial**: el envío por **email** de los eventos (confirmación/cancelación de reserva, recibo de pago) ya está implementado, pero la pata **Telegram** quedó diferida a `auth-otp-telegram`. Esa capability ya está hecha (aporta `TelegramPort`, el bot/webhook y `users.telegram_chat_id`), así que ahora podemos completar `notificaciones` enviando también los avisos por Telegram al chat vinculado y al grupo del club.

## What Changes

- El listener de eventos de notificación (`NotificationEventListener`) se amplía para que, además del email, envíe un **mensaje directo por Telegram** cuando el usuario destinatario tiene `telegram_chat_id NOT NULL` (RN-TEL-03), usando `TelegramPort`.
- Publicación en el **grupo del club** (`type=TELEGRAM_GROUP`) usando `system_config.telegram_group_id` cuando esté configurado (p. ej. reserva confirmada / partida con plazas).
- Cada envío Telegram se registra en `notification_log` (`type=TELEGRAM_DIRECT` | `TELEGRAM_GROUP`, `status=SENT|FAILED`, `error_message`).
- **RN-NOT-01:** un fallo de Telegram (cuenta no vinculada, bot bloqueado, timeout) **no bloquea** el flujo de negocio; se registra `FAILED` y el email se envía igualmente.
- Plantillas de texto Telegram para los eventos (reserva confirmada/cancelada, recibo).

## Capabilities

### New Capabilities
<!-- Ninguna: se completa la pata Telegram de la capability notificaciones ya especificada. -->

### Modified Capabilities
<!-- Sin cambios de requisitos: el spec de notificaciones ya define el comportamiento Telegram (Requirements 1–2, RN-TEL-03, RN-NOT-01). Este change es de implementación. -->

## Impact

- **Backend (`notificaciones`):** amplía `NotificationEventListener`; nuevo adapter/servicio que compone el texto Telegram y llama a `TelegramPort`; nuevas entradas en `notification_log`. Depende de `mensajeria.TelegramPort` (auth-otp-telegram) y de leer `telegram_chat_id` (users) y `telegram_group_id` (system_config).
- **Config:** clave `telegram_group_id` en `system_config` (id del grupo; opcional — sin ella no se publica en grupo).
- **Sin cambios de esquema** salvo, si no existe ya, la clave `telegram_group_id` en `system_config`.
- **Fase del producto:** fase-1 (Wave 4B).

## Fuera de alcance

- Confirmar/cancelar reserva **desde** Telegram (comandos entrantes vía webhook para operaciones de negocio): fuera de esta capability.
- Recordatorios programados adicionales más allá de los ya existentes.
- El token/grupo reales los aporta el operador en `system_config` al desplegar; sin ellos el `TelegramPort` degrada a no-op.
