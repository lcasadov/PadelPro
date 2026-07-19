## Context

El webhook `POST /api/bot/telegram` (módulo `mensajeria`, `TelegramWebhookService`) hoy valida el secret (RN-TEL-01) y despacha un único comando: `/vincular XXXXXX`. Todo lo demás cae en `handleOtherMessage`, que solo responde "vincula primero" a chats no vinculados. La infraestructura reutilizable ya existe: `TelegramPort.enviarMensaje(chatId, texto)` (salida), `OtpService` + `OtpType.RESERVATION_CONFIRM`/`CANCELLATION_CONFIRM` (definidos, sin cablear), `userRepositoryPort.findByTelegramChatId` (mapeo chat→usuario), y el ciclo de vida de reservas del módulo `reservas` (`CrearReservaService.crear`, `CancelarReservaService.cancelar`, `ReservaQueryService.listForUser/getForUser`, `DisponibilidadService.getDisponibilidad`).

Restricciones: el bot NO reimplementa reglas de negocio de reservas — las delega a los servicios existentes (RN-RES-*: anti-solape, precio en backend, plazo de cancelación). Las operaciones críticas (confirmar/cancelar) exigen OTP (RN-AUTH-07). Los IDs de reserva son UUID, poco manejables en un chat.

## Goals / Non-Goals

**Goals:**
- Convertir `parseAndDispatch` en un dispatcher extensible por comando sin romper `/vincular`.
- Reusar íntegramente los servicios de `reservas`, `otp` y `usuarios`; el bot es una capa de adaptación conversacional.
- Formato estricto y mensajes de error legibles para cada comando.
- Auditar cada operación y rechazo (RN-RGPD-04: sin OTP en claro).

**Non-Goals:**
- Parsing de lenguaje natural libre (formato estricto + ayuda).
- Editar/reprogramar reservas o unirse a partidas por bot.
- Cambiar el contrato del webhook. (El esquema de BD recibe una única columna additiva y nullable `otp_codes.reservation_id` — migración `V21`, reversible — para ligar el OTP a la reserva; ver D-4.)

## Decisions

**D-1 · Dispatcher por comando dentro de `mensajeria`.** Se refactoriza `parseAndDispatch` a un mapa comando→handler (`/vincular`, `/reservar`, `/misreservas`, `/cancelar`, `/confirmar`, `/ayuda`). Alternativa descartada: un módulo de bot nuevo separado — innecesario, el webhook y el `TelegramPort` ya viven en `mensajeria` y añadiría acoplamiento sin beneficio. Motiva: RN-TEL-01 (el secret ya se valida antes del dispatch, se mantiene).

**D-2 · Referencia corta de reserva = prefijo del UUID (primeros 8 hex), resuelta por listado del usuario.** `/cancelar` y `/confirmar` reciben esa referencia; el handler la resuelve contra `ReservaQueryService.listForUser(userId)` y exige unicidad de prefijo (si colisiona, pide el id completo). Alternativa descartada: índice numérico de `/misreservas` — requeriría estado conversacional persistente por chat; el prefijo es *stateless* y estable. Evita cambio de esquema. Motiva: usabilidad en chat + RN de propiedad (solo reservas propias).

**D-3 · Reutilización directa de servicios de aplicación, no de los controllers REST.** El handler invoca `CrearReservaService`/`CancelarReservaService`/`ReservaQueryService` con el `userId` resuelto del chat, pasando `admin=false`. Alternativa descartada: llamar a los endpoints REST internamente — añadiría auth HTTP y serialización redundante. Motiva: RN-RES-* provienen del servicio; el bot no las duplica.

**D-4 · Flujo OTP de confirmar/cancelar en dos pasos, con el OTP ligado a la reserva.** `/reservar` emite un `RESERVATION_CONFIRM` y `/cancelar <ref>` un `CANCELLATION_CONFIRM` (enviados por `TelegramPort`); la aplicación efectiva ocurre al validar con `/confirmar <ref> <otp>`.

Decisión clave (opción **a**, tras verificación adversarial): **cada OTP de reserva se liga a su `reservation_id`** (columna nullable additiva en `otp_codes`, migración `V21`; los OTP no-reserva `TELEGRAM_LINK`/`PASSWORD_RESET` la dejan `NULL`). El dispatcher resuelve la operación por **la reserva referenciada**, no por precedencia global de tipo: `peekActiveReservationType(userId, reservationId)` (lectura, sin gastar intento) devuelve el tipo del OTP activo ligado a esa reserva → `RESERVATION_CONFIRM` ⇒ confirmar, `CANCELLATION_CONFIRM` ⇒ cancelar; `verifyForReservation(userId, reservationId, code)` valida ese código concreto. Así `/confirmar X` **nunca** aplica la operación pendiente de otra reserva Y ni gasta el intento del OTP de Y.

Alternativa descartada (opción b, sin esquema): decidir por el estado de `<ref>` — sigue siendo ambigua cuando una misma reserva PENDING tiene a la vez un confirm y un cancel pendientes. La ligadura al `reservation_id` es la única que elimina el cruce por completo. Efecto colateral positivo: generar el OTP de una reserva solo invalida el anterior **de esa misma reserva**, de modo que múltiples reservas pendientes son confirmables en paralelo (antes solo la última). Motiva: RN-AUTH-07 (OTP como segundo factor), reusando `OtpService`.

**D-5 · Parser estricto de `/reservar`.** Regex/tokenización estricta: `<fecha ISO> <hora HH:mm> [duración min] [@handle|nombre externo...]`. Sin campo de pista (la reserva es por tramo; `CrearReservaRequest` no lo tiene). Si no casa → mensaje de ayuda con ejemplo. Los `@handle` se resuelven a usuarios vinculados; tokens no-@ se tratan como participante externo (nombre). Motiva: evita el riesgo de NL libre marcado en el backlog (US-017).

## Risks / Trade-offs

- **[Colisión de prefijo UUID en la referencia corta]** → Mitigación: exigir unicidad dentro de las reservas del usuario; si hay colisión, el bot pide el UUID completo. El espacio (8 hex = 4.3e9) hace la colisión improbable con pocas reservas por usuario.
- **[Parsing frágil del comando de creación]** → Mitigación: formato estricto documentado en `/ayuda`, mensajes de error con ejemplo, y cobertura de tests de parsing (casos válidos e inválidos). No se intenta adivinar intención.
- **[Resolución de @handles a usuarios]** → Mitigación: si un `@handle` no resuelve a una cuenta vinculada, se rechaza el comando indicando qué participante falló, en vez de crear la reserva a medias.
- **[Doble ejecución por reintentos del webhook de Telegram]** → Mitigación: reusar la `idempotencyKey` de `CrearReservaService.crear` (derivada de chatId+comando+tramo) para que un reintento no cree dos reservas. Confirmar en implementación la fuente de la clave.
- **[OTP en logs]** → Mitigación: RN-RGPD-04 — nunca loguear el código; auditar solo la acción y el resultado.
- **[Estado pendiente ambiguo confirmar vs cancelar]** → Mitigación (D-4, opción a): el OTP se liga a `reservation_id`; `/confirmar <ref>` resuelve el OTP activo **de esa reserva** y su tipo (`RESERVATION_CONFIRM` vs `CANCELLATION_CONFIRM`) determina la operación. Con confirms/cancels pendientes en reservas distintas no hay cruce posible.

## Migration Plan

1. Refactor de `parseAndDispatch` a dispatcher (sin cambio de comportamiento para `/vincular`) + tests de regresión de vinculación.
2. Implementar handlers de solo-lectura primero (`/ayuda`, `/misreservas`) — sin efectos, bajo riesgo.
3. `/reservar` (parser + `CrearReservaService`), luego `/cancelar` y `/confirmar` (flujo OTP).
4. Auditoría + endurecimiento de errores.
5. **Rollback**: el dispatcher degrada a solo `/vincular` revirtiendo el commit del dispatcher; no hay migración de BD que revertir (salvo que D-2 cambie y se persista estado — en ese caso, migración additiva y reversible).
6. Operador: `setWebhook` ya está; el bot token real ya se aporta en `system_config`. Sin token, `TelegramPort` degrada a no-op.

## Open Questions

- ¿La `idempotencyKey` de creación debe derivarse del `update_id` de Telegram (más robusto frente a reintentos) o de chatId+tramo? Resolver en implementación.
- ¿`durationMinutes` es fija por configuración del club o la acepta el comando? (memoria del proyecto apunta a duración fija en el flujo web). Si es fija, `/reservar` omite el parámetro.
