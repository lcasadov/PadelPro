## Context

El módulo `notificaciones` ya reacciona a eventos de dominio (`ReservationConfirmedEmailEvent`, `ReservationCancelledEmailEvent`, `PaymentPaidEmailEvent`) en `NotificationEventListener`, envía email vía `NotificationPort` (SMTP adapter) y persiste `notification_log` vía `NotificationLogPort`. `auth-otp-telegram` aportó `mensajeria.TelegramPort.enviarMensaje(chatId, texto)` (best-effort, nunca lanza) y `users.telegram_chat_id`. Falta cablear el envío Telegram a esos mismos eventos.

## Goals / Non-Goals

**Goals:**
- Enviar aviso Telegram directo al destinatario con `telegram_chat_id` en los eventos ya cubiertos por email.
- Publicar en el grupo del club cuando `telegram_group_id` esté configurado.
- Registrar cada envío en `notification_log`; el fallo nunca rompe el negocio (RN-NOT-01).

**Non-Goals:**
- Comandos entrantes de negocio por Telegram (webhook) — fuera de alcance.
- Nuevos eventos de dominio; se reutilizan los existentes.

## Decisions

### D1 — Extender el listener existente, no duplicar el flujo (RN-TEL-03, RN-NOT-01)
El `NotificationEventListener` gana un paso Telegram tras el email: por cada evento, si el destinatario tiene `telegram_chat_id`, compone el texto y llama a `TelegramPort`. El puerto ya es best-effort (no lanza), y cada intento se envuelve para registrar `SENT`/`FAILED` en `notification_log` sin propagar excepción (RN-NOT-01). Se evita un segundo listener para no duplicar la resolución del destinatario ni el mapeo evento→plantilla.

### D2 — `telegram_group_id` en `system_config` (coherente con el token)
El id del grupo vive en `system_config` junto al `telegram_bot_token`/`telegram_webhook_secret` (D-OTP-01). No requiere cifrado (un chat id no es secreto), pero se gestiona por el mismo servicio de config. Si está vacío, no se publica en grupo (degradación limpia).

### D3 — Reutilizar `notification_log` con `type` Telegram
`NotificationType` ya contempla `TELEGRAM_DIRECT` y `TELEGRAM_GROUP`. Cada envío crea su fila (`recipient` = chat id, `message` = texto, `related_entity_type/id` = reserva/pago), igual que el email. El `NotificationRetryJob` existente puede reintentar los `FAILED` si su diseño ya lo contempla para Telegram; si no, los `FAILED` quedan registrados para auditoría (sin bloquear).

### D4 — Plantillas de texto Telegram separadas de las de email
Textos cortos en texto plano (o Markdown de Telegram) por evento, análogos a las plantillas de email pero sin HTML. Se aíslan en su propia clase/paquete para no mezclar con las plantillas SMTP.

## Risks / Trade-offs

- **[Doble notificación molesta (email + Telegram)]** → Mitigación: es el comportamiento especificado (RN: ambos canales para vinculados). No se cambia.
- **[Fallo/timeout de la API de Telegram]** (RN-NOT-01) → Mitigación: `TelegramPort` best-effort + envoltura que registra `FAILED` sin lanzar; la transacción de negocio ya está commiteada cuando se notifica (`@Async`/afterCommit según el patrón existente del listener).
- **[Grupo no configurado]** → Mitigación: sin `telegram_group_id` no se intenta publicar en grupo (no cuenta como fallo).
- **[Fuga de datos personales en el grupo]** → Mitigación: los mensajes de grupo no incluyen datos personales sensibles (solo franja/estado), coherente con RN-RGPD.

## Migration Plan

1. Añadir (si falta) `telegram_group_id` a `system_config` (columna/clave nullable).
2. Desplegar; sin token/grupo configurados, el envío Telegram degrada a no-op y solo se envía email.
3. Operador configura `telegram_bot_token` + `telegram_group_id` en `system_config` cuando el bot esté listo.
4. Rollback: revertir el código; el email sigue funcionando igual (cambio aditivo).
