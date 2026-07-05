# Partidas — ver y unirse a reservas con plazas libres (fase-1)

> Orden TDD (Red → Green → Refactor). Reutiliza el modelo de `reservas` (Participant.registered, Reservation.addParticipant, isActiveOccupant). Sin migraciones previstas. Pago compartido online DIFERIDO a `pagos-redsys` (aquí importe informativo).

## 1. Backend — listado de partidas abiertas (D1)

- [x] 1.1 **[Red]** Test: listar partidas abiertas de una fecha devuelve reservas activas con plazas libres, cada una con `reservaId`, hora, duración, plazas, participantes (display), importe informativo; sin auth → 401; reserva completa no aparece
- [x] 1.2 **[Green]** Endpoint de listado de partidas abiertas (dedicado `GET /api/partidas` o extensión acordada) + servicio que reutiliza el cálculo de ocupación/plazas; proyección sin PII (solo nombres de display); documentar en `docs/openapi.yaml`

## 2. Backend — unirse a una partida (D2)

- [x] 2.1 **[Red]** Tests del caso de uso `UnirseReserva`: unión exitosa (participante no-owner, siguiente slot); ya participante → 409 (RN-AUTH-03); reserva completa → 422 (RN-RES-02); estado CANCELLED/COMPLETED → 422; reserva inexistente → 404
- [x] 2.2 **[Red]** Test de concurrencia: dos uniones simultáneas a la última plaza → una 200, otra 422 (no supera `max_participants`)
- [x] 2.3 **[Green]** `UnirseReservaService`: carga reserva (404), valida estado activo (422), duplicado (409), plazas atómicamente bajo transacción/bloqueo (422), calcula siguiente `slot_position`, persiste `Participant.registered(is_owner=false)`; invalida caché de disponibilidad
- [x] 2.4 **[Green]** Puerto/adapter para añadir participante a reserva existente (hoy solo hay `save` de creación completa)
- [x] 2.5 **[Green]** `POST /api/reservas/{id}/unirse` en `ReservaController` + DTO `UnirseResponse` (participanteId, reservaId, userId, nombre, statusPago); alinear `docs/openapi.yaml` con lo implementado

## 3. Backend — abandonar una partida (D3)

- [x] 3.1 **[Red]** Tests: participante no-owner abandona → plaza liberada; owner intenta abandonar → rechazo (debe cancelar); no participante → error
- [x] 3.2 **[Green]** Endpoint de abandono (`DELETE /api/reservas/{id}/participacion`) + servicio que elimina la fila de `participants` del usuario; invalida caché de disponibilidad; documentar en openapi

## 4. Frontend — pantallas de partidas

- [x] 4.1 **[Red]** Tests (MSW): `PartidasAbiertasPage` lista partidas (reservaId, hora, plazas); estado vacío; error
- [x] 4.2 **[Green]** `PartidasAbiertasPage` (mockup 21) + funciones en `reservasApi.ts` (listar partidas) + ruta en `reservasPaths.ts`/`App.tsx` + acceso desde Home
- [x] 4.3 **[Red]** Tests: `ConfirmarUnionPage` muestra detalle + "tu parte" informativo; unirse con éxito → confirmación; 409 ya participante → mensaje claro sin checkout; 422 completa → mensaje
- [x] 4.4 **[Green]** `ConfirmarUnionPage` (mockup 22) + `unirseReserva` en `reservasApi.ts`; importe informativo (sin cobro online); abandono desde el detalle/mis reservas si aplica

## 5. QA

- [ ] 5.1 **[Refactor]** Limpieza manteniendo verde
- [ ] 5.2 `verification-specialist`: build + tests (backend maven, frontend vitest) + lint; probes de concurrencia y validaciones
- [x] 5.3 `security-auditor`: acceso al listado (no PII), autorización de unirse/abandonar, no exponer datos de otros socios (RN-RGPD-03)
- [ ] 5.4 `reality-checker`: journey end-to-end (ver partidas abiertas → unirse → aparece en mis reservas → abandonar) — live E2E puede diferirse si requiere despliegue
- [x] 5.5 Actualizar `docs/openapi.yaml` y verificar coherencia del contrato de `/unirse`, listado y abandono

### Notas de correcciones (pasada de fixes backend, 2026-07-05)

- **[MEDIO — RGPD] Minimización de PII en el detalle/listado para co-participantes (RN-RGPD-03).**
  `ReservaMapper.toResponse` ahora recibe `includePii`; `ReservaQueryService.getForUser`/`listForUser`
  lo calculan como `admin || solicitante==owner`. Un co-jugador que se unió a la partida recibe
  `externalPhone` de los invitados y `notes` de la reserva en `null`; owner y ADMIN siguen viéndolo
  todo. La regla de acceso 403 para no-participantes NO cambia. Documentado en `docs/openapi.yaml`
  (`ReservaResponse.notes`, `ParticipanteResponse.externalPhone`).
  Tests IT nuevos en `ReservaControllerIntegrationTest` (owner ve / ADMIN ve / co-participante null /
  no-participante 403 / listado minimizado).
- **[BAJO — backstop] Índice único parcial `uq_part_user_reservation (reservation_id, user_id) WHERE user_id IS NOT NULL`.**
  Migración `V12__unique_participant_per_reservation.sql`. Defensa en profundidad de RN-AUTH-03 a
  nivel BD (el anti-duplicado en memoria bajo el lock sigue siendo la primera línea). Flyway aplica
  V1..V12 limpio sobre el PG de test.
- **[Gaps de test] Estados terminales.** IT nuevos: unirse a reserva `COMPLETED` → 422
  `RESERVA_NOT_JOINABLE`; abandonar reserva `CANCELLED` y `COMPLETED` → 422 `RESERVA_NOT_JOINABLE`.
- Resultado: `ReservaControllerIntegrationTest` 31/31, `UnirseAbandonarIntegrationTest` 14/14 — 45 verdes contra Postgres real.
