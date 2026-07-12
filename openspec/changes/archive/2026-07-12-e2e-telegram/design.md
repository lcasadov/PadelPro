## Context

`auth-otp-telegram`: la vinculación es en dos pasos — la app genera un OTP `TELEGRAM_LINK` y muestra `/vincular <código>`; el usuario lo envía al bot; el **webhook** `POST /api/bot/telegram` (validado con `X-Telegram-Bot-Api-Secret-Token` contra `system_config.telegram_webhook_secret`, RN-TEL-01) fija `telegram_chat_id`. `GET /api/usuarios/me` expone `telegramLinked`. Sin bot real, el envío saliente es no-op, pero el webhook entrante SÍ se puede ejercitar desde el test.

## Goals / Non-Goals

**Goals:** cubrir E2E el round-trip de vinculación (el test hace de bot vía webhook), la desvinculación, el camino "aún no vinculado", y la seguridad del webhook (403).
**Non-Goals:** entrega real por la Bot API; comandos de negocio por bot.

## Decisions

### D1 — El test actúa como el bot (webhook)
Como no hay bot real, el test simula el mensaje entrante: `page.request.post('/api/bot/telegram', { headers: { 'X-Telegram-Bot-Api-Secret-Token': <secret> }, data: <update de Telegram con text '/vincular <código>' y un chat.id> })`. Es la forma determinista de completar la vinculación end-to-end.

### D2 — Configurar el webhook secret para el test
El webhook exige que `system_config.telegram_webhook_secret` esté fijado y coincida. El setup del test lo fija vía la API admin de configuración (el mismo endpoint/campo que usa el panel; explorar `SystemConfigService`/`UpdateSystemConfigRequest` y el controller admin de config). Se usa un secret conocido por el test. Si el endpoint no permite fijarlo de forma sencilla, documentar y reducir el alcance (al menos el 403 y el "no vinculado" no requieren secret).

### D3 — Leer el `otpCode`
El código para `/vincular` lo devuelve el `PATCH /api/usuarios/me {telegramAction:LINK}` (campo `otpCode`) y la UI lo muestra. El test lo obtiene de la forma más robusta: preferir leerlo del cuerpo de la respuesta del PATCH (interceptando la petición o llamando al API para el setup), o del DOM de la pantalla de vinculación. Nunca hardcodear.

### D4 — Autolimpieza / re-ejecutable
Cada spec deja la cuenta como la encontró: el de round-trip **desvincula** al final; los demás no vinculan. Así la suite es re-ejecutable sobre la misma BD.

## Risks / Trade-offs

- **[chat_id de test reutilizado entre runs]** → Mitigación: desvincular al final; usar un chat_id de test estable; el backend rechaza vincular un chat ya usado por otra cuenta (solo hay el admin, así que ok).
- **[Config del webhook secret no accesible por API]** → Mitigación (D2): reducir alcance a 403 + "no vinculado", documentado.
- **[Rate-limit de login]** → Mitigación: reutilizar el `login()` resiliente de `helpers.ts`.

## Migration Plan

1. Añadir specs en `e2e/tests/`.
2. Verificar en verde contra el stack real (local) y en CI.
3. Rollback: eliminar los specs.
