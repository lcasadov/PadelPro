## ADDED Requirements

### Requirement: Crear bloqueos de franjas horarias

**El sistema DEBE (MUST) permitir al ADMIN bloquear una o varias franjas horarias (granularidad 60 min) de una fecha, con un motivo, siempre que ninguna franja solicitada solape una reserva activa. Si alguna solapa, la operación completa se rechaza informando de los conflictos.**

#### Scenario: ADMIN bloquea franjas libres

- **GIVEN** un administrador autenticado con `role=ADMIN`
- **AND** la fecha `2026-08-01` no tiene reservas activas a las 18:00 ni 19:00
- **WHEN** envía `POST /api/admin/bloqueos` con `{ "fecha": "2026-08-01", "horas": ["18:00","19:00"], "motivo": "Torneo" }`
- **THEN** el sistema responde `201`
- **AND** existen filas de bloqueo para `(2026-08-01, 18:00)` y `(2026-08-01, 19:00)` con el motivo y `created_by` del ADMIN
- **AND** la acción `BLOQUEO_CREATED` se registra en `audit_log`

#### Scenario: Bloqueo rechazado por reserva activa en conflicto

- **GIVEN** un ADMIN autenticado
- **AND** existe una reserva activa que ocupa la franja de las 19:00 del `2026-08-01`
- **WHEN** envía `POST /api/admin/bloqueos` con `{ "fecha":"2026-08-01", "horas":["18:00","19:00"], "motivo":"Torneo" }`
- **THEN** el sistema responde `409` (o `422`) con cuerpo `ErrorResponse`
- **AND** el cuerpo enumera las franjas en conflicto (19:00) y la(s) reserva(s) afectada(s)
- **AND** NO se crea ningún bloqueo de esa petición (operación todo-o-nada)

#### Scenario: Bloqueo idempotente sobre una franja ya bloqueada

- **GIVEN** un ADMIN autenticado
- **AND** la franja `(2026-08-01, 18:00)` ya está bloqueada
- **WHEN** vuelve a enviar un bloqueo que incluye `18:00` (sin reservas en conflicto)
- **THEN** el sistema responde `201` (o `200`) sin crear un duplicado
- **AND** sigue existiendo exactamente una fila para `(2026-08-01, 18:00)`

#### Scenario: Un USER no puede crear bloqueos

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** envía `POST /api/admin/bloqueos`
- **THEN** el sistema responde `403` (ACCESS_DENIED)

### Requirement: Listar bloqueos de una fecha

**El sistema DEBE (MUST) permitir al ADMIN consultar los bloqueos existentes de una fecha.**

#### Scenario: ADMIN lista los bloqueos de una fecha

- **GIVEN** un ADMIN autenticado
- **AND** las franjas 18:00 y 19:00 del `2026-08-01` están bloqueadas
- **WHEN** envía `GET /api/admin/bloqueos?fecha=2026-08-01`
- **THEN** el sistema responde `200` con la lista de bloqueos de esa fecha (hora, motivo, id)

### Requirement: Eliminar un bloqueo

**El sistema DEBE (MUST) permitir al ADMIN eliminar un bloqueo, liberando esa franja para reservas.**

#### Scenario: ADMIN desbloquea una franja

- **GIVEN** un ADMIN autenticado
- **AND** existe un bloqueo con `id=7` en `(2026-08-01, 18:00)`
- **WHEN** envía `DELETE /api/admin/bloqueos/7`
- **THEN** el sistema responde `204`
- **AND** la franja `(2026-08-01, 18:00)` deja de estar bloqueada
- **AND** la acción `BLOQUEO_DELETED` se registra en `audit_log`
- **AND** la disponibilidad de esa fecha vuelve a ofrecer la franja de las 18:00 (si tiene plazas)
