## Context

`partidas` reutiliza el modelo de `reservas`: una "partida abierta" es una reserva activa (`PENDING_CONFIRMATION`/`CONFIRMED`) con `participants_count < system_config.max_participants`. La exploración confirma que las piezas de dominio existen (`Participant.registered(...)`, `Reservation.addParticipant(...)`, `Reservation.isActiveOccupant()` con los estados correctos, cálculo de plazas en `DisponibilidadService`), pero **no hay flujo de unión** ni forma de identificar la reserva joinable desde el cliente (`TramoDisponible` no lleva `reservaId`). El Requirement 3 (listar participaciones) ya lo cubre `GET /api/reservas`.

Restricción de pago: los mockups muestran pago compartido ("tu parte"), pero `Payment` es 1:1 con la reserva y del owner, y `pagos-redsys` no existe. Se difiere el cobro, igual que en `reservas`.

## Goals / Non-Goals

**Goals:**
- Ver las partidas abiertas (reservas joinable) identificables por `reservaId`.
- Unirse a una reserva con plazas libres, con validaciones 404/409/422 (RN-AUTH-03, RN-RES-02) y estados correctos.
- Abandonar una partida a la que uno se unió (liberar plaza).
- Entregar el flujo social sin bloquear con el pago online.

**Non-Goals:**
- Cobro por participante online (Redsys) — importe solo informativo.
- Concepto público/privado de partida — toda reserva con plazas es joinable.
- Notificaciones / OTP de la unión.

## Decisions

### D1 — Endpoint dedicado `GET /api/partidas` para listar reservas joinable
Se añade un endpoint que devuelve las reservas activas con plazas libres para una fecha (o rango), cada una con `reservaId`, fecha, hora, duración, `plazasLibres`, participantes (nombre/plaza) e importe total (informativo). *Alternativa descartada:* extender `TramoDisponible` con `reservaId` dentro de `/api/reservas/disponibles` — mezcla dos vistas (crear vs unirse), y un tramo puede mapear a varias reservas; un recurso propio de "partidas" es más claro y no toca el contrato de disponibilidad usado por el flujo de crear. Motiva RN-RES-02 (plazas) y RN-AUTH-01 (no revela datos sensibles: solo nombres de display).

### D2 — `POST /api/reservas/{id}/unirse` con validación atómica de plaza
El servicio carga la reserva (404 si no existe), valida estado activo (`isActiveOccupant`; si no → 422 `INVALID_STATE_TRANSITION`/`RESERVA_NOT_JOINABLE`), valida que el usuario no sea ya participante (409 `CONFLICT`, RN-AUTH-03), valida que queden plazas contra `max_participants` (422 `PARTICIPANTS_LIMIT_EXCEEDED`, RN-RES-02), calcula el siguiente `slot_position` (`max(slot)+1`) y persiste `Participant.registered(...)`. La comprobación de plaza + inserción debe ser **atómica** (transacción + bloqueo de la reserva, análogo al `SELECT FOR UPDATE` de creación) para evitar que dos uniones concurrentes superen `max_participants`. *Alternativa descartada:* validar plaza sin bloqueo — condición de carrera que sobrepasa el máximo (mismo patrón que el MEDIO-1 de auth). Reutiliza `Participant.registered()` y `Reservation.addParticipant()`.

### D3 — Abandonar: `DELETE /api/reservas/{id}/participacion` (participante no-owner)
Un participante no-owner puede salir de la reserva (elimina su fila de `participants`, libera plaza). El **owner no puede** abandonar (debe cancelar la reserva vía el flujo existente). *Alternativa considerada:* diferir "abandonar" — pero es barato y completa el flujo social (sin él, unirse por error es irreversible para el jugador). Se incluye en el MVP. Estados: solo con reserva activa; sin reembolso online (no hay cobro).

### D4 — Importe "tu parte" informativo, sin cobro
La UI de confirmar unión muestra `total ÷ nº participantes tras unirse` como referencia (RN-RES-03: el total lo calcula el backend), pero la unión **no** crea `Payment` por participante ni cobra. El cobro compartido llega con `pagos-redsys`. *Alternativa descartada:* crear un `Payment` por participante ahora — exige rediseño del modelo de pago (1:N, FK a participante) fuera de alcance.

## Risks / Trade-offs

- [Dos jugadores se unen a la vez a la última plaza] → Mitigación: validación de plaza + inserción atómica bajo transacción/bloqueo de la reserva (D2); test de concurrencia.
- [Exponer partidas abiertas revela nombres de otros socios] → Mitigación: solo autenticados; se devuelven nombres de display (ya visibles en el detalle de reserva a participantes), sin email ni datos sensibles (RN-RGPD-03).
- [Unirse sin cobro deja la contabilidad incompleta hasta `pagos-redsys`] → Mitigación aceptada: el club cobra presencialmente (MVP CASH), coherente con el resto de `reservas`; el importe informativo evita malentendidos.
- [El owner intenta "abandonar"] → Mitigación: 422/403 claro indicando que debe cancelar la reserva.

## Migration Plan

- Sin migraciones previstas (se reutilizan `reservations`/`participants`; FK ya existe). Confirmar durante la implementación que no hace falta índice nuevo para el listado de partidas.
- Despliegue: backend + frontend aditivos y retrocompatibles. Rollback = revertir ambos; no afecta a crear/cancelar reservas existentes.

## Open Questions

Resueltas en revisión (2026-07-05):
- **D1** → endpoint dedicado `GET /api/partidas`.
- **D3** → "abandonar" entra en el MVP.
- **Importe** → "tu parte" informativo (sin cobro online).
