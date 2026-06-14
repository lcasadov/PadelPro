### API Contract

Endpoint `GET /api/reservas/disponibles` — ver `docs/openapi.yaml`, operationId `getDisponibilidad`, tag `Reservas`, y schemas `DisponibilidadResponse` / `TramoDisponible`. Implementado en `com.padelpro.reservas` (migración Flyway `V7`).

## ADDED Requirements

### Requirement: Consulta de disponibilidad de la pista

El sistema SHALL devolver, para una fecha concreta solicitada por un usuario autenticado, los tramos horarios de la pista con el número de plazas libres calculado a partir de las reservas activas, el máximo de participantes configurado y el estado operativo de la pista.

#### Scenario: Día sin ninguna reserva activa

- **WHEN** un usuario autenticado (USER o ADMIN) envía `GET /api/reservas/disponibles?fecha=2025-08-01` y no existe ninguna reserva con `reservation_date = 2025-08-01` y `status <> 'CANCELLED'`
- **THEN** el sistema responde `200` con la fecha y la lista `tramosDisponibles` con todos los tramos del horario del club
- **AND** cada tramo tiene `plazasLibres` igual a `system_config.max_participants`

#### Scenario: Tramo con reserva parcialmente ocupada

- **WHEN** un usuario autenticado consulta una fecha en la que existe una reserva `CONFIRMED` de `start_time=10:00`, `duration_minutes=60` con 2 participantes y `system_config.max_participants = 4`
- **THEN** el sistema responde `200`
- **AND** el tramo `horaInicio: "10:00"`, `duracionMinutos: 60` aparece en `tramosDisponibles` con `plazasLibres: 2`

#### Scenario: Múltiples reservas en distintos tramos

- **WHEN** un usuario consulta una fecha con reservas `CONFIRMED` en 09:00–10:00 (3 participantes) y 11:00–12:00 (4 participantes) y el tramo 10:00–11:00 sin reserva activa
- **THEN** el tramo 09:00 aparece con `plazasLibres: 1`
- **AND** el tramo 10:00 aparece con `plazasLibres` igual a `system_config.max_participants`
- **AND** el tramo 11:00 NO aparece (0 plazas libres)

### Requirement: Reservas pendientes de confirmar bloquean franja

El sistema SHALL contar las reservas en estado `PENDING_CONFIRMATION` igual que las `CONFIRMED` al calcular las plazas libres, y SHALL excluir siempre las reservas en estado `CANCELLED`.

#### Scenario: Reserva pendiente reduce plazas libres

- **WHEN** un usuario consulta una fecha con una reserva `PENDING_CONFIRMATION` de 1 participante en el tramo 18:00–19:00 y `system_config.max_participants = 4`
- **THEN** el tramo 18:00 aparece con `plazasLibres: 3`

#### Scenario: Reserva cancelada no ocupa franja

- **WHEN** un usuario consulta una fecha cuyo único registro en el tramo 12:00–13:00 es una reserva con `status = 'CANCELLED'`
- **THEN** el tramo 12:00 aparece con `plazasLibres` igual a `system_config.max_participants`

### Requirement: Pista en MANTENIMIENTO devuelve disponibilidad vacía

El sistema SHALL devolver `tramosDisponibles` vacío para cualquier fecha cuando la pista está en estado MANTENIMIENTO, sin revelar el motivo en la respuesta.

#### Scenario: Consulta con pista en mantenimiento

- **WHEN** un usuario autenticado consulta `GET /api/reservas/disponibles?fecha=2025-09-01` y `system_config.pista_state = MANTENIMIENTO`
- **THEN** el sistema responde `200` con `tramosDisponibles: []`
- **AND** la respuesta no incluye ningún campo que revele que el motivo es MANTENIMIENTO

### Requirement: Validación del parámetro de fecha

El sistema SHALL exigir el parámetro `fecha` en formato `YYYY-MM-DD` y SHALL rechazar las peticiones con formato inválido o sin el parámetro.

#### Scenario: Formato de fecha inválido

- **WHEN** un usuario autenticado envía `GET /api/reservas/disponibles?fecha=15-06-2025`
- **THEN** el sistema responde `400` con `code: "VALIDATION_ERROR"`
- **AND** el mensaje indica que el formato debe ser `YYYY-MM-DD`

#### Scenario: Parámetro fecha ausente

- **WHEN** un usuario autenticado envía `GET /api/reservas/disponibles` sin el parámetro `fecha`
- **THEN** el sistema responde `400` con `code: "VALIDATION_ERROR"`

### Requirement: Autenticación obligatoria para consultar disponibilidad

El sistema SHALL exigir un JWT válido para consultar la disponibilidad; las peticiones no autenticadas SHALL recibir `401`.

#### Scenario: Petición sin autenticación

- **WHEN** se envía `GET /api/reservas/disponibles?fecha=2025-08-01` sin cabecera `Authorization`
- **THEN** el sistema responde `401`

#### Scenario: Petición con token expirado

- **WHEN** se envía la consulta con un JWT expirado
- **THEN** el sistema responde `401` y no devuelve datos de disponibilidad
