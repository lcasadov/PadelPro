## 1. Andamiaje del dispatcher

- [x] 1.1 Tests de regresión de `/vincular` sobre `TelegramWebhookService` (garantizar que el refactor no rompe la vinculación existente)
- [x] 1.2 Refactor de `parseAndDispatch` a un dispatcher comando→handler (mapa/registro), manteniendo la validación de secret (RN-TEL-01) antes del parseo
- [x] 1.3 Handler por defecto: comando desconocido responde con el texto de `/ayuda`
- [x] 1.4 Utilidad de resolución chat→usuario (`findByTelegramChatId`) + respuesta "vincula primero" para chats no vinculados

## 2. Comandos de solo lectura

- [x] 2.1 Test unitario de `/ayuda` (lista los comandos y formato)
- [x] 2.2 Implementar handler `/ayuda`
- [x] 2.3 Tests unitarios de `/misreservas` (con reservas / sin reservas / referencia corta estable)
- [x] 2.4 Implementar `/misreservas` reutilizando `ReservaQueryService.listForUser`, formateando referencia corta (prefijo UUID, D-2), fecha, hora y estado

## 3. Crear reserva (`/reservar`)

- [x] 3.1 Tests del parser estricto: casos válidos e inválidos (fecha/hora/duración/@jugadores), mensaje de ayuda ante formato inválido
- [x] 3.2 Implementar parser estricto de `/reservar <fecha> <hora> [duración] [@jugador...]` (sin campo de pista)
- [x] 3.3 Resolución de participantes: `@handle`→usuario vinculado (`findByLogin`), token libre→participante externo; rechazo legible si un `@handle` no resuelve
- [x] 3.4 Tests de creación: éxito, solape rechazado (reutiliza validación del servicio), idempotencia ante reintento del webhook (`idempotencyKey` = `tg-<update_id>`)
- [x] 3.5 Implementar handler `/reservar` invocando `CrearReservaService.crear(ownerId, request, idempotencyKey)`; derivar `idempotencyKey` (Open Question D — `update_id` de Telegram) y responder con referencia + estado

## 4. Confirmar y cancelar con OTP

- [x] 4.1 Tests de `/cancelar` (dos pasos): emisión de OTP `CANCELLATION_CONFIRM`, aplicación tras confirmación, fuera de plazo rechazado, reserva ajena rechazada
- [x] 4.2 Implementar `/cancelar <ref>`: resolver referencia corta a UUID (unicidad, D-2), emitir OTP `CANCELLATION_CONFIRM` vía `OtpService` + `TelegramPort`
- [x] 4.3 Tests de `/confirmar` (OTP válido/ inválido/ expirado/ intentos, RN-AUTH-07)
- [x] 4.4 Implementar `/confirmar <ref> <otp>`: validar OTP (`RESERVATION_CONFIRM`/`CANCELLATION_CONFIRM` según estado pendiente) y aplicar la operación (`CancelarReservaService.cancelar(id, userId, admin=false)` o `ConfirmarReservaService.confirmar(id, userId)`)

## 5. Auditoría y errores

- [x] 5.1 Añadir acciones de auditoría `TELEGRAM_RESERVA_CREATED`, `TELEGRAM_RESERVA_CONFIRMED`, `TELEGRAM_RESERVA_CANCELLED`, `TELEGRAM_COMMAND_REJECTED`
- [x] 5.2 Registrar cada operación y rechazo; verificar por test que ningún log contiene OTP en claro (RN-RGPD-04)
- [x] 5.3 Revisión de mensajes de error conversacionales (legibles, accionables) para todos los comandos

## 6. Integración y verificación

- [ ] 6.1 Test de integración del webhook (Testcontainers): update JSON → dispatch → efecto en BD, para `/reservar`, `/confirmar`, `/cancelar`
- [ ] 6.2 Test E2E de la pata Telegram: journey vincular → reservar → confirmar → cancelar (extiende `e2e-telegram`)
- [ ] 6.3 Actualizar `/ayuda` y documentación de comandos; verificación adversarial (permisos, chat no vinculado, reserva ajena, formato inválido)
- [ ] 6.4 Actualizar `openspec/specs/` (nueva capability) al archivar el change
