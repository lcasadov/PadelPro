# Capability: auth-otp-telegram

## Resumen

Gestiona la vinculación de la cuenta de PadelPro con el chat personal de Telegram del usuario, la generación y verificación de códigos OTP para operaciones críticas (confirmación de reserva, cancelación, reset de contraseña), y la desvinculación de la cuenta. El canal Telegram actúa como segundo factor de seguridad para operaciones de alto impacto.

## Fase

Fase 1 — ✅ Implementada (change `auth-otp-telegram-impl`, Issue #207): módulos `otp` + `mensajeria`, migración `otp_codes` + link Telegram, webhook validado, `POST /api/otp/verificar`, y UI de vinculación en Mi perfil.

## Reglas de negocio implicadas

- **RN-AUTH-07**: OTP de 6 dígitos, TTL 10 min, máximo 3 intentos, almacenado como SHA-256, de un solo uso. Tras 3 intentos fallidos el OTP se invalida automáticamente.
- **RN-TEL-01**: El webhook de Telegram valida `X-Telegram-Bot-Api-Secret-Token` antes de procesar cualquier update. Requests sin el header o con valor incorrecto responden `403`.
- **RN-TEL-02**: La vinculación de cuenta requiere OTP confirmado vía Telegram (TTL 10 min).
- **RN-TEL-03**: Solo las cuentas con `telegram_chat_id NOT NULL` reciben notificaciones por Telegram.
- **RN-RGPD-04**: Los logs no contienen códigos OTP en claro.

## Entidades implicadas

**users** (tabla `users`):
- `id`, `login`, `telegram_chat_id` (NULL si no vinculado), `telegram_linked_at`, `status`

**otp_codes** (tabla `otp_codes`):
- `id`, `user_id`, `code` (almacenado como SHA-256), `type` (`TELEGRAM_LINK` | `RESERVATION_CONFIRM` | `CANCELLATION_CONFIRM` | `PASSWORD_RESET`), `expires_at` (10 min), `used`, `created_at`

## Endpoints

| Método | Path | OperationId | Autenticación |
|---|---|---|---|
| `POST` | `/api/otp/verificar` | `verificarOtp` | JWT requerido (USER o ADMIN) |
| `PATCH` | `/api/usuarios/me` | `actualizarMiPerfil` | JWT requerido | (para iniciar/revocar vinculación) |
| `POST` | `/api/bot/telegram` | webhook interno | Secret token header (RN-TEL-01) |

> La vinculación se inicia a través de `PATCH /api/usuarios/me` con el campo de vinculación, y se completa cuando el usuario envía el OTP al bot de Telegram. El webhook `POST /api/bot/telegram` procesa el mensaje del bot.

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Iniciar vinculación Telegram | Permitido | Permitido | Denegado (401) |
| Verificar OTP (`POST /api/otp/verificar`) | Permitido | Permitido | Denegado (401) |
| Desvincular Telegram | Permitido | Permitido (propio) | Denegado (401) |
| Recibir webhook Telegram | No aplica | No aplica | Solo con secret token |

## Requirements

### Requirement 1: Vinculación de cuenta con Telegram

**El sistema DEBE permitir que un usuario autenticado vincule su cuenta de PadelPro con su chat personal de Telegram mediante un OTP temporal de tipo TELEGRAM_LINK.**

#### Scenario: Vinculación exitosa en dos pasos

- **GIVEN** un usuario autenticado con `telegram_chat_id IS NULL`
- **WHEN** el usuario solicita la vinculación de Telegram a través de su perfil
- **THEN** el sistema genera un OTP de 6 dígitos de tipo `TELEGRAM_LINK` con TTL 10 minutos (RN-TEL-02)
- **AND** el OTP se almacena como SHA-256 en `otp_codes` (RN-AUTH-07)
- **AND** el sistema devuelve instrucciones: "Envía `/vincular XXXXXX` al bot @PadelProBot"

- **WHEN** el usuario envía `/vincular XXXXXX` al bot de Telegram desde su chat personal
- **AND** el webhook `POST /api/bot/telegram` recibe el update con el header `X-Telegram-Bot-Api-Secret-Token` válido
- **THEN** el sistema valida el OTP (SHA-256 del código, no expirado, no usado, tipo correcto)
- **AND** el sistema actualiza `users SET telegram_chat_id = <chat_id>, telegram_linked_at = now()`
- **AND** el OTP queda marcado como `used=true`
- **AND** el bot responde al usuario: confirmación de vinculación exitosa
- **AND** el sistema registra la acción `TELEGRAM_LINKED` en `audit_log`

#### Scenario: Vinculación rechazada por OTP expirado (RN-AUTH-07)

- **GIVEN** un usuario tiene un OTP de tipo `TELEGRAM_LINK` con `expires_at` anterior al momento actual
- **WHEN** el usuario envía el comando de vinculación con ese OTP al bot
- **THEN** el sistema responde con código `422` en el procesamiento interno
- **AND** el bot responde al usuario: "El código ha expirado. Solicita uno nuevo desde tu perfil."
- **AND** el OTP no se usa y no se crea ninguna vinculación

#### Scenario: Vinculación rechazada porque el chat_id ya está vinculado a otra cuenta

- **GIVEN** el `telegram_chat_id` del usuario ya está registrado en otro `users.id`
- **WHEN** el usuario envía el comando de vinculación con un OTP válido
- **THEN** el sistema rechaza la vinculación
- **AND** el bot responde: "Este Telegram ya está vinculado a otra cuenta. Contacta con el administrador."
- **AND** no se modifica ninguna cuenta

---

### Requirement 2: Verificación de OTP para operaciones críticas

**El sistema DEBE verificar códigos OTP para confirmar operaciones de alto impacto. Un OTP es válido solo si no ha expirado, no ha sido usado y el número de intentos fallidos es menor de 3 (RN-AUTH-07).**

#### Scenario: Verificación exitosa de OTP RESERVATION_CONFIRM

- **GIVEN** el usuario tiene un OTP de tipo `RESERVATION_CONFIRM` válido (no expirado, no usado)
- **WHEN** se envía `POST /api/otp/verificar` con el `otpCode` correcto y `type=RESERVATION_CONFIRM`
- **THEN** el sistema responde con código `200`
- **AND** el OTP queda marcado como `used=true`
- **AND** la operación asociada (confirmación de reserva) puede proceder

#### Scenario: Verificación fallida con OTP incorrecto — conteo de intentos

- **GIVEN** el usuario tiene un OTP de tipo `RESERVATION_CONFIRM` válido con 0 intentos previos
- **WHEN** se envía `POST /api/otp/verificar` con un `otpCode` incorrecto
- **THEN** el sistema responde con código `422`
- **AND** el contador interno de intentos fallidos para ese OTP se incrementa
- **AND** el OTP sigue siendo válido para un nuevo intento (si hay menos de 3 fallos)

#### Scenario: OTP invalidado automáticamente tras el 4º intento (RN-AUTH-07)

- **GIVEN** el usuario ha fallado exactamente 3 intentos de verificación del mismo OTP
- **WHEN** se envía `POST /api/otp/verificar` con cualquier código
- **THEN** el sistema responde con código `422`
- **AND** el OTP queda marcado como `used=true` (invalidado automáticamente)
- **AND** el mensaje indica que el código ha sido invalidado por exceso de intentos
- **AND** el usuario debe solicitar un nuevo OTP

#### Scenario: Verificación fallida con OTP ya utilizado

- **GIVEN** existe un OTP con `used=true` para el usuario
- **WHEN** se envía `POST /api/otp/verificar` con ese código
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que el código ya fue utilizado, sin revelar información adicional

---

### Requirement 3: Desvinculación de cuenta Telegram

**El sistema DEBE permitir al usuario autenticado desvincular su cuenta de Telegram, eliminando el `telegram_chat_id` y revocando los OTPs activos relacionados.**

#### Scenario: Desvinculación exitosa

- **GIVEN** el usuario tiene `telegram_chat_id NOT NULL`
- **WHEN** el usuario solicita la desvinculación a través de su perfil (`PATCH /api/usuarios/me`)
- **THEN** el sistema actualiza `users SET telegram_chat_id = NULL, telegram_linked_at = NULL`
- **AND** todos los OTPs activos del usuario quedan invalidados (`used=true`)
- **AND** el sistema registra la acción `TELEGRAM_UNLINKED` en `audit_log`
- **AND** el usuario deja de recibir notificaciones por Telegram (RN-TEL-03)

#### Scenario: Operación vía Telegram bloqueada para cuentas no vinculadas

- **GIVEN** un usuario sin `telegram_chat_id` vinculado envía un comando al bot de Telegram
- **WHEN** el webhook procesa el mensaje
- **THEN** el bot responde al remitente: "Vincula primero tu cuenta en [URL de perfil]"
- **AND** el sistema no ejecuta ninguna operación de negocio

---

### Requirement 4: Validación de webhook de Telegram

**El sistema DEBE rechazar cualquier request al endpoint del webhook de Telegram que no incluya el header `X-Telegram-Bot-Api-Secret-Token` con el valor correcto (RN-TEL-01).**

#### Scenario: Webhook procesado con secret token válido

- **GIVEN** el sistema tiene configurado `telegram_bot_token` en `system_config`
- **WHEN** Telegram envía `POST /api/bot/telegram` con `X-Telegram-Bot-Api-Secret-Token` correcto
- **THEN** el sistema responde con código `200`
- **AND** el update se procesa normalmente

#### Scenario: Webhook rechazado sin header de secret token (RN-TEL-01)

- **GIVEN** el sistema tiene configurado el webhook secret
- **WHEN** se envía `POST /api/bot/telegram` sin el header `X-Telegram-Bot-Api-Secret-Token`
- **THEN** el sistema responde con código `403`
- **AND** la acción `TELEGRAM_WEBHOOK_INVALID_SECRET` se registra en `audit_log`
- **AND** no se procesa ningún update

#### Scenario: Webhook rechazado con secret token incorrecto (RN-TEL-01)

- **GIVEN** el sistema tiene configurado el webhook secret
- **WHEN** se envía `POST /api/bot/telegram` con un valor incorrecto en `X-Telegram-Bot-Api-Secret-Token`
- **THEN** el sistema responde con código `403`
- **AND** la acción `TELEGRAM_WEBHOOK_INVALID_SECRET` se registra en `audit_log`

---

## Casos límite

- Un usuario puede solicitar un nuevo OTP antes de que el anterior expire. El nuevo OTP invalida el anterior del mismo tipo (`used=true` en el anterior) para evitar acumulación de OTPs activos.
- El OTP nunca se registra en claro en logs ni en respuestas de la API (RN-RGPD-04).
- Si el servicio de Telegram está caído durante la generación del OTP, el sistema devuelve error `503` y no persiste el OTP (para evitar OTPs huérfanos).
- El bot nunca ejecuta operaciones administrativas independientemente del remitente del mensaje.
- La comparación del OTP se realiza comparando `SHA-256(codigo_introducido)` con el `code_hash` almacenado, nunca en claro.

## Dependencias con otras capabilities

- **`auth-local`**: la recuperación de contraseña de `auth-local` consume el servicio OTP de esta capability.
- **`reservas`**: la confirmación y cancelación de reservas vía bot Telegram generan OTPs de tipo `RESERVATION_CONFIRM` y `CANCELLATION_CONFIRM`.
- **`usuarios`**: la vinculación se refleja en el campo `telegram_chat_id` de la tabla `users`.
- **`notificaciones`**: una vez vinculada la cuenta, el módulo de notificaciones puede usar el `telegram_chat_id` para enviar mensajes directos.
- **`auditoria`**: todos los eventos OTP (generación, validación exitosa, fallo, invalidación) se registran en `audit_log`.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 13 | Mi perfil | Mobile | USER | [`14-mi-perfil.html`](../../../docs/ux/mockups/14-mi-perfil.html) |
| 14 | Vincular Telegram | Mobile | USER | [`15-vincular-telegram.html`](../../../docs/ux/mockups/15-vincular-telegram.html) |
| 15 | Introducir OTP | Mobile | USER | [`16-otp-telegram.html`](../../../docs/ux/mockups/16-otp-telegram.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de vinculación Telegram** — cubre el flujo completo: desde Mi perfil (pantalla 13) donde el usuario inicia la vinculación, pasando por las instrucciones del bot (pantalla 14), hasta la introducción del OTP de 6 dígitos (pantalla 15).

### Notas de UX

> - El OTP caduca a los 10 minutos; la pantalla de introducción de OTP (pantalla 15) debe mostrar un contador de tiempo restante en monospace (RN-AUTH-07).
> - Tras 3 intentos fallidos el OTP se invalida automáticamente; la pantalla debe indicar que el código ha sido invalidado y ofrecer la opción de solicitar uno nuevo (RN-AUTH-07).
> - El OTP nunca aparece en claro en la pantalla de vinculación; el usuario lo recibe directamente en Telegram y lo introduce manualmente (RN-AUTH-07).
