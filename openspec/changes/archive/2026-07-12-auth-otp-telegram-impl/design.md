## Context

`auth-otp-telegram` está especificada al completo (RN-AUTH-07, RN-TEL-01..03, RN-RGPD-04) pero sin código. El backend ya tiene: `system_config` con cifrado AES-256-GCM (capability `configuracion-club`), `audit_log` + servicio de auditoría, `users` con `status`, y `SecurityConfig` con JWT. Este change implementa la capability sobre esa base, sin cambiar sus requisitos.

## Goals / Non-Goals

**Goals:**
- Servicio OTP robusto y reusable (RN-AUTH-07) con almacenamiento SHA-256.
- Vinculación/desvinculación Telegram en dos pasos con webhook validado (RN-TEL-01/02).
- `TelegramPort` desacoplado: real (Bot API) en prod, stub en tests → cobertura sin llamar a Telegram.
- Frontend de perfil/vincular/OTP según mockups 13–15.

**Non-Goals:**
- Cableado de notificaciones de eventos por Telegram (follow-up de `notificaciones`).
- Registrar el webhook en Telegram ni desplegar el token real (runbook/operador).

## Decisions

### D-OTP-01 — Token y webhook secret en `system_config` cifrados (RN-TEL-01)
El `telegram_bot_token` y el `telegram_webhook_secret` viven en `system_config`, cifrados con AES-256-GCM (misma `ENCRYPTION_KEY` que el resto de secretos de club), y se descifran en runtime. Alternativa (env vars) descartada: mantiene coherencia con cómo el proyecto ya gestiona secretos de club y permite gestionarlos desde el panel admin. El operador introduce el token real tras crear el bot en @BotFather.

### D-OTP-02 — OTP almacenado como SHA-256, comparación por hash (RN-AUTH-07, RN-RGPD-04)
Se persiste `SHA-256(codigo)` en `otp_codes.code_hash`, nunca el código en claro; la verificación compara hashes. El código en claro solo existe en memoria el tiempo de generarlo y enviarlo por Telegram. Logs sin OTP en claro (RN-RGPD-04).

### D-OTP-03 — Conteo de intentos e invalidación automática (RN-AUTH-07)
`otp_codes` lleva `attempts` (int). Cada verificación fallida incrementa `attempts`; al alcanzar 3 fallos, el OTP se marca `used=true` (invalidado) y ya no es verificable. Un nuevo OTP del mismo `type` para el mismo usuario invalida el anterior (`used=true`) para evitar acumulación (caso límite del spec).

### D-OTP-04 — Webhook: validación por header antes de procesar (RN-TEL-01)
`POST /api/bot/telegram` es `permitAll` en `SecurityConfig` (Telegram no manda JWT), pero el controller **rechaza con 403** si falta o no coincide `X-Telegram-Bot-Api-Secret-Token` con el secret de `system_config`, registrando `TELEGRAM_WEBHOOK_INVALID_SECRET` en auditoría. Solo tras validar se parsea el update y el comando `/vincular XXXXXX`.

### D-OTP-05 — `TelegramPort` hexagonal
Puerto de salida `TelegramPort.enviarMensaje(chatId, texto)` con adapter real (HTTP `sendMessage` a la Bot API usando el token descifrado) y un stub/no-op para tests y para entornos sin token (degradación tolerante, coherente con cómo `notificaciones` trata el email). El envío nunca rompe el flujo de negocio.

## Risks / Trade-offs

- **[Token/secret ausentes en un entorno]** → Mitigación (RN-TEL-03): sin token, el `TelegramPort` real degrada a no-op y el webhook responde controladamente; la generación de OTP de vinculación devuelve instrucciones pero no puede completarse hasta configurar el bot. Documentado en runbook.
- **[Fuga de OTP en logs]** (RN-RGPD-04) → Mitigación: nunca loggear el código en claro; solo ids/tipos. Test que verifica ausencia del código en logs donde aplique.
- **[Reuso/replay de OTP]** → Mitigación: un solo uso (`used=true`), TTL 10 min, SHA-256, máx. 3 intentos (D-OTP-02/03).
- **[chat_id ya vinculado a otra cuenta]** → Mitigación: constraint/consulta previa; el webhook rechaza y avisa sin modificar cuentas (escenario del spec).
- **[Cobertura del adapter HTTP real]** → Mitigación: el adapter se prueba con un servidor HTTP mockeado (MockWebServer/WireMock) o se aísla para que la lógica testeable (servicio OTP, verificación, webhook) alcance el 80%.

## Migration Plan

1. Flyway `V<n>__otp_codes_and_telegram_link.sql`: tabla `otp_codes` + columnas en `users` (nullable, sin backfill).
2. Desplegar código; sin token configurado, el comportamiento degrada a no-op (sin romper nada existente).
3. Operador: crear bot en @BotFather, guardar `telegram_bot_token` + `telegram_webhook_secret` en config, registrar el webhook (`setWebhook` con el secret). Runbook.
4. Rollback: la migración es aditiva (columnas nullable + tabla nueva); revertir el código no requiere bajar el esquema.
