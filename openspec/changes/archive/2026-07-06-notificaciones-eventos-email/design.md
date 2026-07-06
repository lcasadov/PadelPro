## Context

La infra de email existe: `NotificationPort` (puerto de salida) + `SmtpNotificationAdapter` (JavaMailSender) + `WelcomeEmailTemplate`, usada por el email de bienvenida (Req 7-8 ✅, asíncrono y tolerante a fallos). Falta: la tabla `notification_log`, las plantillas de eventos, los enganches en los servicios de reserva/pago, y el job de reintentos. Los puntos de transición ya existen: reserva → `CONFIRMED` (`AdminReservaService.cambiarEstado`), reserva → `CANCELLED` (`CancelarReservaService`), pago → `PAID` (`ProcesarWebhookService` y `RegistrarPagoEfectivoService`). El envío de bienvenida ya es `@Async` y no revierte la activación si falla — mismo patrón a seguir.

Las patas Telegram requieren `auth-otp-telegram` (bot, `telegram_chat_id`), inexistente → fuera de alcance.

## Goals / Non-Goals

**Goals:**
- El titular recibe email al confirmarse su reserva, al cancelarse, y al confirmarse el pago.
- Cada envío se registra en `notification_log` (SENT/FAILED) — RN-NOT-03.
- Un fallo de envío nunca revierte ni bloquea el negocio (RN-NOT-01/02); se reintenta hasta 3 veces (Req 3).

**Non-Goals:**
- Telegram (directo y grupo), recordatorio previo, endpoint de lectura, notificar a no-titulares.

## Decisions

### D1 — Disparo desde los servicios de aplicación vía el puerto existente, asíncrono y post-commit
Los enganches se colocan en los servicios de aplicación de reserva/pago, tras confirmarse la transición, invocando un servicio de notificación que envía por `NotificationPort` de forma `@Async` (o vía un `@TransactionalEventListener(AFTER_COMMIT)` para garantizar que la notificación solo sale si la transacción de negocio commiteó). Decisión: **eventos de dominio + listener AFTER_COMMIT** para desacoplar y garantizar el orden (negocio commitea → se notifica). *Alternativa descartada:* llamar al envío inline dentro de la transacción — arriesga enviar un email de una reserva cuya transacción luego revierte, y acopla el envío al rollback.

### D2 — `notification_log` como registro de auditoría de envíos
Tabla nueva con los campos de la spec (`type`, `recipient`, `subject`, `message`, `status`, `error_message`, `related_entity_type/id`, `sent_at`, `created_at`, `user_id` FK nullable, y un contador de intentos `attempts`). Cada intento actualiza el estado. `message`/`recipient` nunca contienen OTP/tokens/contraseñas/tarjeta (RN-RGPD-04). *Alternativa considerada:* reutilizar `audit_log` — mezcla auditoría de seguridad con envíos; la spec define una tabla propia.

### D3 — Reintentos con job `@Scheduled` y backoff, tope 3
Un job `@Scheduled` recoge las entradas `FAILED` (o `PENDING`) con `attempts < 3` y reintenta el envío, aplicando backoff (p.ej. no reintentar antes de `created_at/last_attempt + 2^attempts min`). Tras el 3º fallo, queda `FAILED` definitivo. *Alternativa descartada:* `@Retryable` síncrono en el envío — no sobrevive a caídas de SMTP prolongadas ni a reinicios; el job desacoplado es más robusto (RN-NOT-02).

### D4 — Plantillas por evento reutilizando el patrón existente
Plantillas de confirmación, cancelación y recibo siguiendo `WelcomeEmailTemplate` (asunto + cuerpo, datos de la reserva/pago). Sin datos sensibles. *Alternativa considerada:* motor de plantillas (Thymeleaf) — overkill para 3 emails; se mantiene el patrón simple existente.

## Risks / Trade-offs

- [Un email se envía pero el registro en `notification_log` falla] → Mitigación: registrar la entrada `PENDING` antes de enviar y actualizar a SENT/FAILED después; el job reconcilia PENDING antiguos.
- [Doble envío si el listener AFTER_COMMIT se dispara y además el job reintenta] → Mitigación: el envío marca SENT atómicamente; el job solo toma FAILED/PENDING con `attempts<3`; idempotencia por entrada.
- [SMTP del entorno no configurado (MAIL_HOST vacío)] → Mitigación: el adaptador ya falla de forma tolerante; la entrada queda FAILED y el negocio no se afecta (igual que bienvenida hoy).
- [PII en `notification_log`] → Mitigación: `message` solo con datos de la reserva/pago (fecha, importe, referencia), nunca credenciales/OTP/tarjeta (RN-RGPD-04); test que lo verifica.

## Migration Plan

- Una migración Flyway crea `notification_log` (+ índices por `status` para el job y por `related_entity`). Sin cambios en otras tablas.
- Despliegue aditivo: los enganches solo añaden envíos; si el job o el SMTP fallan, el negocio no se ve afectado. Rollback = revertir; las reservas/pagos existentes no cambian.

## Open Questions

Resueltas en revisión (2026-07-06):
- **Disparo** → eventos de dominio + `@TransactionalEventListener(AFTER_COMMIT)`.
- **Eventos** → CONFIRMED + CANCELLED + pago PAID (según spec; sin email de "reserva creada").
- **Recordatorio previo** → fuera de alcance en este change.
