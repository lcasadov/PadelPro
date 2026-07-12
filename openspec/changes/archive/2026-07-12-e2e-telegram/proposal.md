## Why

La cobertura E2E de Telegram (capability `auth-otp-telegram`) es mínima: solo `vincular-telegram` verifica que se muestra el código. Los flujos clave —completar la vinculación (webhook), desvincular, y la seguridad del webhook— no tienen red de seguridad end-to-end. Ampliarla protege el flujo de segundo factor / vinculación contra regresiones, contra el stack real.

## What Changes

- Nuevos specs Playwright en `e2e/tests/` (job `e2e` de CI, sin cambios de workflow):
  - **Vinculación completa (round-trip):** configurar el `telegram_webhook_secret` en `system_config` (vía API admin) → iniciar vinculación en la UI y leer el `otpCode` → el test **actúa como el bot** haciendo `POST /api/bot/telegram` con el secret y el comando `/vincular <código>` → "comprobar vinculación" en la UI → el perfil muestra **vinculado** → **desvincular** → vuelve a **no vinculado**.
  - **Comprobar sin vincular:** iniciar → "comprobar" sin que el webhook haya vinculado → aviso "aún no detectamos la vinculación".
  - **Seguridad del webhook (RN-TEL-01):** `POST /api/bot/telegram` sin el header `X-Telegram-Bot-Api-Secret-Token` o con valor incorrecto → **403** (verificado con `page.request`).
- Todos deterministas, re-ejecutables y autocontenidos (limpian/desvinculan tras de sí).

## Capabilities

### New Capabilities
<!-- Ninguna: amplía la cobertura E2E de auth-otp-telegram / ci-cd-deploy (suite E2E existente). -->

### Modified Capabilities
<!-- Sin cambios de requisitos: implementación de tests. -->

## Impact

- **Testing (`e2e/`):** nuevos `*.spec.ts` de Telegram. Sin cambios de producto ni esquema. El job `e2e` de CI corre la suite ampliada.
- El envío saliente por Telegram queda **SKIPPED** (sin token de bot real configurado) — no se prueba la entrega real, sí el webhook entrante (vinculación) y la seguridad.
- **Fase del producto:** fase-1 (calidad).

## Fuera de alcance

- Entrega real de mensajes por la Bot API (requiere token real + red a Telegram).
- OTP de operaciones críticas (RESERVATION_CONFIRM/CANCELLATION) por bot: no hay flujo de negocio por comando en esta fase.
