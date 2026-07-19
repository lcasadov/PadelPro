## 1. Andamiaje del dispatcher

- [ ] 1.1 Tests de regresión de `/vincular` sobre `TelegramWebhookService` (garantizar que el refactor no rompe la vinculación existente)
- [ ] 1.2 Refactor de `parseAndDispatch` a un dispatcher comando→handler (mapa/registro), manteniendo la validación de secret (RN-TEL-01) antes del parseo
- [ ] 1.3 Handler por defecto: comando desconocido responde con el texto de `/ayuda`
- [ ] 1.4 Utilidad de resolución chat→usuario (`findByTelegramChatId`) + respuesta "vincula primero" para chats no vinculados

## 2. Comandos de solo lectura

- [ ] 2.1 Test unitario de `/ayuda` (lista los comandos y formato)
- [ ] 2.2 Implementar handler `/ayuda`
- [ ] 2.3 Tests unitarios de `/misreservas` (con reservas / sin reservas / referencia corta estable)
- [ ] 2.4 Implementar `/misreservas` reutilizando `ReservaQueryService.listForUser`, formateando referencia corta (prefijo UUID, D-2), fecha, hora y estado

## 3. Crear reserva (`/reservar`)

- [ ] 3.1 Tests del parser estricto: casos válidos e inválidos (fecha/hora/duración/@jugadores), mensaje de ayuda ante formato inválido
- [ ] 3.2 Implementar parser estricto de `/reservar <fecha> <hora> [duración] [@jugador...]` (sin campo de pista)
- [ ] 3.3 Resolución de participantes: `@handle`→usuario vinculado, token libre→participante externo; rechazo legible si un `@handle` no resuelve
- [ ] 3.4 Tests de creación: éxito, solape rechazado (reutiliza validación del servicio), idempotencia ante reintento del webhook
- [ ] 3.5 Implementar handler `/reservar` invocando `CrearReservaService.crear(ownerId, request, idempotencyKey)`; derivar `idempotencyKey` (Open Question D — `update_id` de Telegram) y responder con referencia + estado

## 4. Confirmar y cancelar con OTP

- [ ] 4.1 Tests de `/cancelar` (dos pasos): emisión de OTP `CANCELLATION_CONFIRM`, aplicación tras confirmación, fuera de plazo rechazado, reserva ajena rechazada
- [ ] 4.2 Implementar `/cancelar <ref>`: resolver referencia corta a UUID (unicidad, D-2), emitir OTP `CANCELLATION_CONFIRM` vía `OtpService` + `TelegramPort`
- [ ] 4.3 Tests de `/confirmar` (OTP válido/ inválido/ expirado/ intentos, RN-AUTH-07)
- [ ] 4.4 Implementar `/confirmar <ref> <otp>`: validar OTP (`RESERVATION_CONFIRM`/`CANCELLATION_CONFIRM` según estado pendiente) y aplicar la operación (`CancelarReservaService.cancelar(id, userId, admin=false)` o confirmación de reserva)

## 5. Auditoría y errores

- [ ] 5.1 Añadir acciones de auditoría `TELEGRAM_RESERVA_CREATED`, `TELEGRAM_RESERVA_CONFIRMED`, `TELEGRAM_RESERVA_CANCELLED`, `TELEGRAM_COMMAND_REJECTED`
- [ ] 5.2 Registrar cada operación y rechazo; verificar por test que ningún log contiene OTP en claro (RN-RGPD-04)
- [ ] 5.3 Revisión de mensajes de error conversacionales (legibles, accionables) para todos los comandos

## 6. Integración y verificación

- [ ] 6.1 Test de integración del webhook (Testcontainers): update JSON → dispatch → efecto en BD, para `/reservar`, `/confirmar`, `/cancelar`
- [ ] 6.2 Test E2E de la pata Telegram: journey vincular → reservar → confirmar → cancelar (extiende `e2e-telegram`)
- [ ] 6.3 Actualizar `/ayuda` y documentación de comandos; verificación adversarial (permisos, chat no vinculado, reserva ajena, formato inválido)
- [ ] 6.4 Actualizar `openspec/specs/` (nueva capability) al archivar el change
