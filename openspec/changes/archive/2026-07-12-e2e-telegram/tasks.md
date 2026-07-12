## 1. Specs de Telegram

- [x] 1.1 `telegram-vinculacion-completa.spec.ts` — configurar webhook secret → iniciar (leer `otpCode`) → el test hace de bot (`POST /api/bot/telegram` con secret + `/vincular <código>`) → "comprobar" → perfil vinculado → desvincular → no vinculado
- [x] 1.2 `telegram-comprobar-sin-vincular.spec.ts` — iniciar → "comprobar" sin webhook → aviso "aún no detectamos la vinculación"
- [x] 1.3 `telegram-webhook-seguridad.spec.ts` — `POST /api/bot/telegram` sin/incorrecto `X-Telegram-Bot-Api-Secret-Token` → 403 (RN-TEL-01, vía `page.request`)

## 2. Robustez

- [x] 2.1 Reutilizar `helpers.ts` (`login()` resiliente al rate-limit)
- [x] 2.2 Autolimpieza: el round-trip desvincula al final; re-ejecutables sobre la misma BD
- [x] 2.3 `otpCode` obtenido dinámicamente (respuesta del PATCH o DOM), nunca hardcodeado

## 3. Verificación y cierre

- [x] 3.1 Los 3 specs de Telegram en verde contra el stack real (round-trip completo incluido)
- [x] 3.2 Suite E2E completa en verde: **10/10** (7 previos + 3 Telegram); entra en el job `e2e` de CI
- [x] 3.3 PR

## Notas de implementación
- **Round-trip:** el test configura `telegram_webhook_secret` vía `PATCH /api/admin/sistema/config` (preservando el resto de la config), lee el `otpCode` dinámicamente, y hace de bot con `POST /api/bot/telegram` (`{message:{chat:{id},text:'/vincular <código>'}}`, header `X-Telegram-Bot-Api-Secret-Token`). Verifica `telegramLinked:true` y desvincula al final.
- Los 3 journeys quedaron COMPLETOS (ninguno reducido).
- Rate-limit de login (5/min): el round-trip hace 2 logins; se reutiliza el patrón resiliente al 429 (más lento en suite, pero determinista).
