## Why

La capability `auth-otp-telegram` está **especificada** (`openspec/specs/auth-otp-telegram/spec.md`) pero **no implementada**. Es la Wave 2B del plan y desbloquea: (1) OTP como segundo factor para operaciones críticas, y (2) la pata **Telegram** de `notificaciones` (mensajes directos al chat vinculado). Sin ella, el bot y las notificaciones Telegram no existen.

## What Changes

- **Persistencia:** Flyway crea la tabla `otp_codes` y añade `telegram_chat_id` / `telegram_linked_at` a `users`.
- **Servicio OTP:** generar (6 dígitos, TTL 10 min, SHA-256, un solo uso, máx. 3 intentos), verificar e invalidar (RN-AUTH-07). Un nuevo OTP del mismo tipo invalida el anterior.
- **Endpoint** `POST /api/otp/verificar` (JWT) para operaciones críticas.
- **Vinculación Telegram:** iniciar vía `PATCH /api/usuarios/me` (genera OTP `TELEGRAM_LINK` + instrucciones), completar cuando el usuario envía `/vincular XXXXXX` al bot; desvincular (limpia `telegram_chat_id` + invalida OTPs).
- **Webhook** `POST /api/bot/telegram` con validación `X-Telegram-Bot-Api-Secret-Token` (RN-TEL-01) y parseo del comando `/vincular`.
- **Port de mensajería Telegram** (`TelegramPort`): impl real (HTTP a la Bot API) gated por `telegram_bot_token` de `system_config`; **stub** para tests.
- **Auditoría:** `TELEGRAM_LINKED`, `TELEGRAM_UNLINKED`, `TELEGRAM_WEBHOOK_INVALID_SECRET`.
- **Frontend:** Mi perfil (estado de vinculación + vincular/desvincular), instrucciones del bot, e introducir/gestionar OTP (contador TTL, aviso de invalidación tras 3 fallos).

## Capabilities

### New Capabilities
<!-- Ninguna nueva: se implementan los requisitos ya definidos en la capability auth-otp-telegram. -->

### Modified Capabilities
<!-- Sin cambios de requisitos: la spec de auth-otp-telegram ya define el comportamiento. Este change es de implementación (código + migraciones + tests). -->

## Impact

- **Backend:** nueva migración Flyway; módulo `otp` (dominio/aplicación/infra), controller webhook, `TelegramPort` + adapter; ampliación de `PATCH /api/usuarios/me` y `SecurityConfig` (`/api/otp/**` autenticado, `/api/bot/telegram` permitido con validación por header).
- **Frontend:** pantallas de perfil/vincular/OTP (mockups 13–15).
- **Config:** `telegram_bot_token` y webhook secret en `system_config` (AES-256-GCM, D-OTP-01). Token real lo aporta el operador al desplegar; nunca en el repo.
- **Dependencias:** cliente HTTP para la Bot API (WebClient/RestClient ya disponible en Spring).
- **Fase del producto:** fase-1 (Wave 2B).

## Fuera de alcance

- Confirmar/cancelar reserva vía bot (`RESERVATION_CONFIRM`/`CANCELLATION_CONFIRM` end-to-end desde Telegram) más allá del servicio OTP genérico: el flujo completo de esas operaciones por bot se aborda cuando `reservas`/`notificaciones` lo consuman.
- Envío real de notificaciones de eventos por Telegram (pata de `notificaciones`): este change deja el `telegram_chat_id` disponible y el `TelegramPort`; el cableado de eventos es follow-up de `notificaciones`.
- Registro del webhook en Telegram (setWebhook) y despliegue del token real: pasos de runbook/operador.
