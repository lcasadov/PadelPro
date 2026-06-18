## Context

`disponibilidad-pistas` (US-006, #13) es la consulta de solo lectura que muestra qué tramos horarios de la pista única tienen plazas libres para una fecha. Hoy no existe ninguna tabla de reservas en la BD (Flyway va hasta `V6__create_system_config_table.sql`), por lo que esta capability **introduce el modelo de datos de reservas** además del endpoint de consulta.

Restricciones y estado actual:
- Una sola pista física en v1.0 (no hay tabla `courts`; ver `openspec/specs/pistas/spec.md`).
- La configuración global (`system_config`, singleton) ya existe e expone `max_participants` y `pista_state` (capability `configuracion-club`, implementada).
- El schema de `reservations` y `participants` está documentado al detalle en `docs/data-model.md` §3.3/§3.4, incluyendo el constraint anti-solapamiento.
- Stakeholders: jugadores (consumen disponibilidad antes de reservar) y la futura capability `reservas` (Wave 3), que escribirá sobre estas tablas.

## Goals / Non-Goals

**Goals:**
- Crear la migración Flyway `V7` con `reservations` + `participants` fiel a `docs/data-model.md`, **correcta desde el primer día** (la barrera anti-solapamiento no puede cambiarse con datos en producción).
- Implementar `GET /api/reservas/disponibles?fecha=YYYY-MM-DD` con cálculo de plazas libres por tramo (RN-RES-01, RN-RES-02) y respeto del estado MANTENIMIENTO (RN-RES-04).
- Validación robusta del parámetro `fecha` y autorización (USER/ADMIN sí, anónimo 401).
- Dejar las tablas listas y el constraint operativo para que Wave 3 solo tenga que escribir.

**Non-Goals:**
- Creación, confirmación o cancelación de reservas (`POST/PATCH /api/reservas`) — Wave 3.
- Tabla `payments` y lógica de pago — Wave 3/4A.
- Partidas joinables — Wave 4C.
- Frontend del calendario (TICKET-005, #6).

## Decisions

### D1 — Schema de `reservations`: `tsrange` + `EXCLUDE USING gist` (cierra D-RES-01)
**Decisión:** columnas explícitas `start_time`/`end_time`/`duration_minutes` **más** el constraint `excl_res_no_overlap` `EXCLUDE USING gist (reservation_date WITH =, tsrange(date+start, date+end, '[)') WITH &&) WHERE (status <> 'CANCELLED')`, previa `CREATE EXTENSION IF NOT EXISTS btree_gist`.
**Motiva (RN-RES-01):** el anti-solapamiento de franjas debe garantizarse a nivel de BD de forma atómica; ninguna concurrencia de aplicación puede colar dos reservas activas que se pisen.
**Alternativa considerada:** solo columnas `(start_time, duration_minutes)` con validación en lógica de aplicación. **Descartada:** sin bloqueo a nivel de motor, dos transacciones concurrentes pueden crear reservas solapadas (carrera). Cerrada por Luis y coincidente con `docs/data-model.md` §3.3.

### D2 — Crear `participants` junto con `reservations` en `V7`
**Decisión:** la migración crea ambas tablas (y enums) en la misma versión.
**Motiva (RN-RES-02):** las plazas libres de un tramo se calculan como `max_participants − COUNT(participants activos)`. La consulta de disponibilidad necesita `participants` para los tramos con reserva parcial; no puede calcularse solo con `reservations`.

### D3 — Origen de `max_participants` y estado de pista: `system_config`
**Decisión:** el servicio de disponibilidad lee `max_participants` y `pista_state` del singleton `system_config` (capability `configuracion-club`), no de constantes.
**Motiva (RN-RES-02, RN-RES-04):** las plazas por tramo y el bloqueo por MANTENIMIENTO son configurables por el ADMIN en runtime.

### D4 — Tratamiento de estados para el cálculo
**Decisión:** se consideran ocupantes las reservas con `status IN ('PENDING_CONFIRMATION','CONFIRMED')` (es decir, `status <> 'CANCELLED'` y `<> 'COMPLETED'` para fechas futuras); `CANCELLED` nunca cuenta.
**Motiva (RN-RES-01):** una reserva pendiente de confirmar ya bloquea la franja; lo contrario permitiría overbooking transitorio. Alinea con el índice parcial `idx_res_date_status WHERE status <> 'CANCELLED'`.

### D5 — MANTENIMIENTO devuelve lista vacía sin revelar motivo
**Decisión:** si `pista_state = MANTENIMIENTO`, responder `200` con `tramosDisponibles: []` y sin campo que indique el motivo.
**Motiva (RN-RES-04):** no exponer información operativa innecesaria; la UX muestra mensaje genérico.

### D6 — Caché de 30 s por fecha, con hook de invalidación
**Decisión:** cachear el resultado por clave `fecha` con TTL 30 s y exponer un punto de invalidación que Wave 3 invocará al crear/cancelar reservas.
**Motiva:** la disponibilidad se consulta con alta frecuencia y cambia poco; 30 s acota la inconsistencia. La invalidación explícita evita mostrar franjas ya ocupadas tras una reserva.

## Risks / Trade-offs

- **[La extensión `btree_gist` puede no estar disponible/permitida en el entorno destino]** → La migración usa `CREATE EXTENSION IF NOT EXISTS btree_gist`; documentar en `docs/PROJECT.md`/despliegue que el rol de BD necesita privilegio para crear extensiones, o pre-crearla en el bootstrap del contenedor PostgreSQL. Verificar con Testcontainers que la migración aplica limpiamente.
- **[Migración irreversible en la práctica]** → El constraint gist no admite cambios cómodos con datos. Mitigación: nace ya con la forma definitiva (D1) y se cubre con test de migración antes de cualquier dato de producción; rollback solo por `DROP TABLE` mientras las tablas estén vacías.
- **[Ventana de caché de 30 s puede mostrar una franja como libre justo tras una reserva]** → Hook de invalidación por fecha desde Wave 3; mientras Wave 3 no exista, no hay escritura, así que el riesgo es nulo en esta entrega.
- **[Desalineación entre el cálculo de tramos y el horario del club]** → El horario de apertura/cierre y la granularidad de tramos provienen de `system_config`; si esos campos no estuvieran poblados, usar los defaults de la fila inicial (`V6`) y cubrir el caso con test.

## Migration Plan

1. Añadir `V7__create_reservations_and_participants.sql`: `CREATE EXTENSION IF NOT EXISTS btree_gist`; enums `reservation_status`, `reservation_channel`; tablas `reservations` y `participants` con columnas, FKs, índices y constraints de `docs/data-model.md` §3.3/§3.4 (incl. `excl_res_no_overlap`).
2. Verificar con Testcontainers que `V1..V7` aplican limpiamente sobre PostgreSQL 15 desde cero.
3. Implementar capa de lectura (entidad, puerto, repositorio, servicio, DTOs, controlador) y el endpoint `GET /api/reservas/disponibles`.
4. **Rollback:** mientras `reservations`/`participants` estén vacías, revertir = bajar la migración (`DROP TABLE participants; DROP TABLE reservations; DROP TYPE ...`). Tras tener datos, no hay rollback seguro del constraint gist → exige nueva migración correctiva.

## Open Questions

- Ninguna bloqueante. D-RES-01 resuelta (D1). Pendiente menor de confirmar al implementar: si el horario de apertura/cierre y la duración de tramo deben leerse de campos concretos de `system_config` o derivarse de defaults — se resolverá leyendo el esquema real de la fila inicial de `V6` durante `/opsx:apply`.
