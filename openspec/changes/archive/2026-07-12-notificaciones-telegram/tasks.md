## 1. Config del grupo

- [x] 1.1 Añadir `telegram_group_id` a `system_config` (Flyway si es columna nueva; nullable) + lectura vía el servicio de config existente
- [x] 1.2 Exponer la clave en el request/servicio de actualización de config admin (como `telegram_bot_token`/`telegram_webhook_secret`)

## 2. Plantillas Telegram

- [x] 2.1 Textos Telegram (texto plano / Markdown de Telegram) para: reserva confirmada, reserva cancelada, recibo de pago
- [x] 2.2 Aislar en su propio paquete/clase (no mezclar con plantillas SMTP)

## 3. Envío Telegram en el listener

- [x] 3.1 Ampliar `NotificationEventListener`: tras el email, si el destinatario tiene `telegram_chat_id`, componer texto y enviar por `TelegramPort` (RN-TEL-03)
- [x] 3.2 Publicación en grupo (`TELEGRAM_GROUP`) cuando `telegram_group_id` esté configurado
- [x] 3.3 Registrar cada envío en `notification_log` (`TELEGRAM_DIRECT`/`TELEGRAM_GROUP`, `SENT`/`FAILED`, `error_message`, `related_entity_*`)
- [x] 3.4 RN-NOT-01: envolver el envío para que un fallo NO propague excepción ni bloquee el flujo (el email se envía igualmente)

## 4. Tests (cobertura ≥80% del código nuevo)

- [x] 4.1 Reserva confirmada con `telegram_chat_id` → email + Telegram directo, dos entradas `SENT`
- [x] 4.2 Reserva confirmada sin `telegram_chat_id` → solo email, sin intento Telegram
- [x] 4.3 Fallo de Telegram (puerto lanza/no-op) → flujo continúa, `notification_log` `FAILED`, email igualmente enviado
- [x] 4.4 Publicación en grupo cuando hay `telegram_group_id`; no publica cuando falta
- [x] 4.5 Cancelación y recibo de pago → aviso Telegram correspondiente

## 5. QA y cierre

- [x] 5.1 Backend `mvn -DskipITs test` en verde (57 tests notificaciones+mensajeria; suite completa sin regresión)
- [x] 5.2 Auto-revisión: `TelegramPort` ahora reporta SENT/FAILED/SKIPPED → `notification_log` FAILED con error real (spec Req 2 esc. 3/4). Bug corregido: `findRetriable` filtra a EMAIL (no reenvía Telegram como email).
- [x] 5.3 `openspec/plan.md` actualizado (notificaciones ✅ completa) + PR

## Notas de implementación
- Pata Telegram cableada en `NotificationEventListener` (directo al `telegram_chat_id` + grupo vía `telegram_group_id`). RN-NOT-01: fallo Telegram no bloquea email ni negocio.
- `TelegramPort.enviarMensaje` cambió de `void` a `TelegramSendResult` (SENT/FAILED/SKIPPED) — sin token = SKIPPED (no audita), fallo real = FAILED con motivo saneado. El consumidor de auth-otp-telegram ignora el retorno.
- `notification_log.subject` es NOT NULL (heredado de email); para Telegram se guarda etiqueta corta "Telegram".
