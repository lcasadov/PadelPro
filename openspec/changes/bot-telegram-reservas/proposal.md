## Why

El MVP cerró con la gestión de reservas **solo por web**: Telegram quedó como canal de OTP entrante (`/vincular`) y notificaciones salientes. La gestión de reservas *desde* Telegram (EP-04 del backlog original: US-017 crear, US-018 confirmar, US-019 cancelar) fue **descope explícito** de `auth-otp-telegram` y `notificaciones-telegram` ("Fuera de alcance: confirmar/cancelar/crear reserva desde Telegram"), difiriéndolo a "un follow-up que lo consuma" que nunca se creó. Este change cierra ese hueco: los jugadores de un club de pádel viven en el grupo de Telegram, y poder reservar/confirmar/cancelar sin salir de la app es el canal natural. La infraestructura ya está ~80% construida, así que el coste incremental es acotado.

## What Changes

- **Dispatcher de comandos en el webhook**: `TelegramWebhookService.parseAndDispatch` se amplía de un único comando (`/vincular`) a un despachador que reconoce `/reservar`, `/misreservas`, `/cancelar`, `/confirmar` y `/ayuda`, delegando cada uno a un handler dedicado. Todo mensaje de un chat **no vinculado** sigue recibiendo "vincula primero".
- **`/reservar <fecha> <hora> [duración] [@jugador...]`**: crea una reserva reutilizando `CrearReservaService.crear(ownerId, CrearReservaRequest, idempotencyKey)`. Formato **estricto** con mensaje de ayuda si no casa. Nota: la reserva es por **tramo** (fecha+hora+duración), no por pista concreta — `CrearReservaRequest` no tiene campo de pista. Los `@jugador` se resuelven a usuarios vinculados; nombres libres se tratan como participante externo.
- **`/confirmar <ref> <otp>`**: confirma una reserva pendiente validando un OTP `RESERVATION_CONFIRM` (tipo ya definido en `OtpType`, hoy sin cablear) vía `OtpService`.
- **`/cancelar <ref>`**: inicia cancelación; emite OTP `CANCELLATION_CONFIRM` y, tras confirmación, invoca `CancelarReservaService.cancelar(id, userId, admin=false)`.
- **`/misreservas`**: lista las reservas del usuario (`ReservaQueryService.listForUser`) con una **referencia corta** manejable en el chat (índice o primeros 8 chars del UUID), ya que los IDs son UUID.
- **`/ayuda`**: enumera los comandos y su formato.
- **Mapeo chat→usuario y RBAC**: cada comando resuelve el usuario por `telegram_chat_id` (`findByTelegramChatId`); solo opera sobre reservas propias (rol `USER`). Reutiliza la validación de solape/anti-overlap y el ciclo de vida atómico del módulo `reservas` — sin duplicar reglas de negocio.
- **Auditoría**: nuevas acciones (`TELEGRAM_RESERVA_CREATED`, `TELEGRAM_RESERVA_CONFIRMED`, `TELEGRAM_RESERVA_CANCELLED`, `TELEGRAM_COMMAND_REJECTED`).

## Capabilities

### New Capabilities
- `bot-telegram-reservas`: comandos entrantes de Telegram para crear, listar, confirmar y cancelar reservas por parte de un jugador vinculado, incluyendo parsing estricto, resolución chat→usuario, flujos con OTP para operaciones críticas y respuestas conversacionales de error.

### Modified Capabilities
<!-- Ninguna a nivel de requisitos. El webhook `POST /api/bot/telegram` (capability auth-otp-telegram) se amplía de despachar solo `/vincular` a un dispatcher multi-comando, pero eso realiza los requisitos de la NUEVA capability; los requisitos existentes de auth-otp-telegram (validación de secret RN-TEL-01, vinculación, verificación de OTP) no cambian su comportamiento. Los tipos `RESERVATION_CONFIRM`/`CANCELLATION_CONFIRM` de `OtpType` pasan de definidos a consumidos (implementación, no cambio de requisito). -->
- _(ninguna)_

## Impact

- **Backend (`mensajeria`)**: refactor de `TelegramWebhookService` a un dispatcher + handlers por comando; parser estricto de `/reservar`; formateadores de respuesta; nuevas acciones de auditoría. Depende de `reservas` (`CrearReservaService`, `CancelarReservaService`, `ReservaQueryService`, `DisponibilidadService`), `otp` (`OtpService`) y `usuarios` (`findByTelegramChatId`).
- **Sin cambios de esquema** previstos: reutiliza `otp_codes`, `users.telegram_chat_id`, `reservations`/`participants`. A confirmar en design si hace falta persistir estado conversacional (referencia corta ↔ UUID) o si se deriva en cada mensaje.
- **Config**: reutiliza `telegram_bot_token` y el webhook secret de `system_config`; sin el token el `TelegramPort` degrada a no-op (comportamiento existente).
- **Frontend**: ninguno (canal Telegram). El mensaje de `/ayuda` documenta el formato.
- **Seguridad**: toda operación exige chat vinculado + (para confirmar/cancelar) OTP; el webhook ya valida el secret `X-Telegram-Bot-Api-Secret-Token`.
- **Fase del producto**: fase-1 (completa EP-04 del MVP original, diferido en Wave 2B/4B).

## Fuera de alcance

- **Publicar/gestionar reservas en el grupo** más allá de las notificaciones salientes ya existentes (US-020 ya cubierto por `notificaciones-telegram`).
- **Parsing de lenguaje natural libre**: el comando de creación usa formato **estricto** con mensaje de ayuda, no NL.
- **Unirse a partidas abiertas por bot**: se mantiene por web (`partidas`); podría ser un follow-up.
- **Reprogramar/editar** una reserva existente por bot.
- **setWebhook y despliegue del token real**: pasos de runbook/operador.
