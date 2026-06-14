## Why

Los jugadores necesitan ver qué franjas horarias de la pista están libres para una fecha concreta **antes** de poder reservar (US-006, #13). Es la puerta de entrada del flujo de reserva y un prerrequisito directo de Wave 3 (`reservas`): sin consulta de disponibilidad no hay forma de elegir una franja. Además, esta capability obliga a crear por primera vez la tabla `reservations` (y `participants`), cuyo schema —en especial el constraint anti-solapamiento— es un **punto de no retorno** que debe nacer correcto.

**Fase del producto:** fase-1.

## What Changes

- **Migración Flyway `V7`**: crea las tablas `reservations` y `participants`, los enums `reservation_status` y `reservation_channel`, los índices documentados en `docs/data-model.md` §3.3/§3.4 y el constraint de seguridad anti-solapamiento `excl_res_no_overlap` (`EXCLUDE USING gist` sobre `tsrange`, requiere `CREATE EXTENSION btree_gist`). Las tablas nacen **vacías**: la escritura de reservas llega en Wave 3.
- **Endpoint `GET /api/reservas/disponibles?fecha=YYYY-MM-DD`**: devuelve los tramos horarios con plazas libres para la fecha, calculados a partir de (a) las reservas activas (`status <> 'CANCELLED'`) que solapan cada tramo, (b) `system_config.max_participants` para las plazas restantes y (c) el estado operativo de la pista (`system_config.pista_state`).
- **Estado MANTENIMIENTO**: cuando la pista está en MANTENIMIENTO, la respuesta devuelve `tramosDisponibles: []` sin revelar el motivo.
- **Validación de entrada**: `fecha` obligatoria en formato `YYYY-MM-DD`; formato inválido o ausente → `400 VALIDATION_ERROR`.
- **Autorización**: USER y ADMIN autenticados pueden consultar; petición sin JWT → `401`.
- **Caché 30 s** por fecha, invalidable al crearse/cancelarse reservas (el hook de invalidación queda preparado; lo consumirá Wave 3).

## Capabilities

### New Capabilities
<!-- Ninguna nueva: la capability ya está documentada en openspec/specs/ desde el init. -->

### Modified Capabilities
- `disponibilidad-pistas`: se formaliza e implementa el comportamiento ya especificado (consulta de tramos libres, plazas parciales, lista vacía en MANTENIMIENTO, validación del parámetro `fecha`). El delta confirma los requisitos que pasan de "documentados" a "implementados".

## Impact

- **Base de datos**: nueva migración `V7__create_reservations_and_participants.sql`. Extensión `btree_gist`. El constraint `excl_res_no_overlap` es la barrera de seguridad que Wave 3 (`reservas`) usará junto con `SELECT FOR UPDATE`.
- **Backend** (`com.padelpro.auth` / módulo de reservas): nueva entidad `Reservation` (+`Participant`), puerto de lectura, repositorio JPA, servicio de cálculo de disponibilidad, DTOs (`DisponibilidadResponse`, `TramoDisponible`) y `ReservaDisponibilidadController`.
- **Dependencias con otras capabilities**: `configuracion-club` (lee `max_participants` y `pista_state`); `reservas` (Wave 3) consumirá estas tablas y el constraint; `partidas` reutilizará la misma consulta.
- **Issue**: #13 (US-006). **Decisión de diseño D-RES-01**: cerrada → `tsrange + EXCLUDE USING gist` (coincide con `docs/data-model.md` §3.3).
- **Fuera de alcance**: creación/cancelación de reservas (`POST/PATCH /api/reservas`) — Wave 3; tabla `payments` — Wave 3/4A; el frontend del calendario (TICKET-005, #6); la lógica de partidas joinables — Wave 4C.
