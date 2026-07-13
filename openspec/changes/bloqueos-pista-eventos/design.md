## Context

`DisponibilidadService.getDisponibilidad(fecha)` recorre las horas `OPEN_HOUR`(8)–`CLOSE_HOUR`(23) en franjas de `SLOT_MINUTES`(60), calcula `plazasLibres = maxParticipants − ocupación de reservas activas que solapan`, y devuelve los tramos con al menos una plaza. El estado de pista MANTENIMIENTO devuelve lista vacía (RN-RES-04). No hay concepto de bloqueo por franja. La ocupación se obtiene de `ReservationQueryPort.findActiveOccupanciesByDate(fecha)` (solo reservas activas; CANCELLED nunca cuenta, RN-RES-01). La caché `available-slots` (30 s, key=fecha) se invalida vía `DisponibilidadCacheInvalidator.invalidate(fecha)`.

## Goals

- Bloquear/desbloquear horas concretas de una fecha (granularidad 60 min, alineada con la rejilla).
- Impedir crear un bloqueo si hay reservas activas solapando, informando cuáles (decisión del usuario: "impedir y avisar").
- Que una franja bloqueada desaparezca de la disponibilidad del jugador.
- No tocar reservas existentes ni el contrato de disponibilidad.

## Decisions

- **D1 — Modelo `BloqueoPista` = (fecha, hora, motivo, createdBy).** Una fila por franja horaria bloqueada. `hora` = `LocalTime` del inicio del tramo (minuto 0), coherente con `SLOT_MINUTES=60`. Constraint único `(fecha, hora)` para idempotencia y evitar duplicados. Bloquear un rango 17:00–21:00 = crear las filas 17,18,19,20 (el cliente manda la lista de horas; el servicio inserta las que falten).
- **D2 — Migración V19 `bloqueo_pista`**: `id` PK, `fecha DATE NOT NULL`, `hora TIME NOT NULL`, `motivo TEXT`, `created_by_user_id BIGINT`, `created_at TIMESTAMPTZ`, `UNIQUE(fecha, hora)`, índice por `fecha` (la disponibilidad consulta por fecha). Siguiente versión libre tras V18 (pagos-simulador).
- **D3 — Chequeo de conflicto reusa la ocupación de reservas activas.** El servicio de creación pide `findActiveOccupanciesByDate(fecha)` y, para cada hora solicitada, comprueba si alguna ocupación solapa `[hora, hora+60)`. Si hay solape → NO se crea ninguna de las horas en conflicto y se devuelve `409 CONFLICT` (o `422`) con la lista de franjas y reservas en conflicto. Las horas sin conflicto de la misma petición: por atomicidad y previsibilidad, **la operación es todo-o-nada** — si cualquier hora solicitada tiene conflicto, se rechaza la petición completa y el admin ajusta la selección.
- **D4 — Integración en disponibilidad.** `DisponibilidadService` recibe un nuevo puerto `BloqueoQueryPort.findHorasBloqueadasByFecha(fecha)` (Set<LocalTime>). En el bucle de tramos, si `slotStart` está en el set → se omite el tramo (no se añade), igual que si no tuviera plazas. Se añade tras el cálculo de plazas para no revelar el motivo (el jugador solo ve que no está disponible).
- **D5 — Invalidación de caché.** Crear o borrar un bloqueo llama a `DisponibilidadCacheInvalidator.invalidate(fecha)` para que el jugador vea el cambio de inmediato (la caché es de 30 s; se fuerza el evict para consistencia).
- **D6 — Endpoints admin.** `POST /api/admin/bloqueos` (body: `{ fecha, horas:[HH:mm], motivo }`) → 201 con los bloqueos creados, o 409/422 con conflictos. `GET /api/admin/bloqueos?fecha=YYYY-MM-DD` → lista de bloqueos de la fecha. `DELETE /api/admin/bloqueos/{id}` → 204. Todos `@PreAuthorize("hasRole('ADMIN')")`, auditados (`BLOQUEO_CREATED` / `BLOQUEO_DELETED`) siguiendo el patrón de auditoría existente.
- **D7 — UI: rejilla horaria por fecha.** El admin elige una fecha; se pinta la rejilla 8–23 combinando disponibilidad + bloqueos: cada hora es *libre* (checkbox marcable), *reservada* (no marcable, informada) o *bloqueada* (marcada, con botón desbloquear). Guardar envía las horas nuevas a bloquear; el conflicto se muestra inline. Para saber qué horas están reservadas, la UI puede cruzar `GET /api/reservas` de la fecha o reutilizar disponibilidad; se decide en implementación el endpoint admin más simple (posible `GET` de ocupación del día).

## Risks

- **Carrera reserva↔bloqueo**: un jugador podría reservar entre el chequeo y el insert. Mitigado por el constraint y por revalidar; en el peor caso, el bloqueo coexiste con una reserva previa (se prioriza no romper la reserva ya hecha). Aceptable dado el volumen (club pequeño, pista única).
- **Cambio de granularidad futura (30 min)**: el modelo `(fecha, hora)` a 60 min habría que revisarlo; documentado como fuera de alcance.
- **Coherencia con MANTENIMIENTO**: si la pista está en MANTENIMIENTO la disponibilidad ya es vacía; los bloqueos son ortogonales y siguen almacenados. Sin conflicto.

## Migration Plan

- V19 crea `bloqueo_pista` (aditiva, sin datos previos). Rollback de código = revertir el PR; la tabla queda vacía y sin uso (no rompe nada). Flyway no hace down-migrations: si hubiera que retirar la tabla se haría en una migración posterior.
