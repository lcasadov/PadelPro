## ADDED Requirements

### Requirement: Crear reserva en franja libre

El sistema SHALL crear la reserva con estado `PENDING_CONFIRMATION` cuando la franja horaria solicitada está libre, el usuario es ACTIVE y los datos de la petición son válidos. El precio total SHALL calcularse exclusivamente en backend (`price_per_hour × duration_minutes / 60`) y SHALL crearse atómicamente un registro en `payments` con `status=PENDING` y el mismo `amount` congelado (RN-RES-03, US-024).

#### Scenario: USER crea reserva en franja libre con datos válidos
- **WHEN** un USER ACTIVE autenticado envía `POST /api/reservas` con `reservationDate: "2025-06-15"`, `startTime: "09:00"`, `durationMinutes: 60` y no existe reserva activa que solape la franja
- **THEN** el sistema responde 201 con la reserva en `PENDING_CONFIRMATION`
- **AND** `priceTotal` refleja `price_per_hour × (60/60)` calculado en backend
- **AND** el creador aparece como participante con `is_owner: true` y `slot_position: 1`
- **AND** se crea atómicamente un `payments` con `status=PENDING` y el mismo `amount`

#### Scenario: USER crea reserva con participante adicional externo
- **WHEN** un USER ACTIVE envía `POST /api/reservas` con `durationMinutes: 90` y `participantesAdicionales: [{externalName: "Carlos García"}]` para una franja libre
- **THEN** el sistema responde 201 en `PENDING_CONFIRMATION`
- **AND** `participants` contiene el titular (`is_owner: true`) y el participante externo

#### Scenario: Datos de entrada inválidos (fecha pasada)
- **WHEN** un usuario autenticado envía `POST /api/reservas` con `reservationDate` en el pasado
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`

#### Scenario: Importe enviado por el cliente es ignorado
- **WHEN** un usuario autenticado envía `POST /api/reservas` incluyendo un importe arbitrario en el cuerpo
- **THEN** el `amount` del pago y `priceTotal` se calculan en backend e ignoran el valor del cliente (RN-RES-03)

### Requirement: Rechazo de reserva por conflicto de horario

El sistema SHALL rechazar la creación con 409 cuando ya existe una reserva activa (`status <> 'CANCELLED'`) que solapa la franja, garantizado por el constraint `EXCLUDE USING gist` a nivel de BD (RN-RES-01).

#### Scenario: Conflicto de horario directo
- **WHEN** un usuario envía `POST /api/reservas` para 2025-06-15 09:00–10:00 y ya existe una reserva `CONFIRMED` en esa franja
- **THEN** el sistema responde 409 con `code: "CONFLICT"`
- **AND** no se crea ninguna reserva ni registro de pago

#### Scenario: Conflicto de horario parcial
- **WHEN** un usuario envía `POST /api/reservas` con `startTime: "09:00"`, `durationMinutes: 60` y existe una reserva activa 08:00–09:30
- **THEN** el sistema responde 409 con `code: "CONFLICT"`

#### Scenario: Concurrencia — dos usuarios a la misma franja
- **WHEN** dos usuarios envían `POST /api/reservas` simultáneamente para la misma franja libre
- **THEN** exactamente uno recibe 201 y el otro 409
- **AND** el constraint `excl_res_no_overlap` garantiza que no se generan dos reservas solapadas bajo ninguna condición de carrera

### Requirement: Cancelar reserva dentro del plazo con reembolso

El sistema SHALL cancelar la reserva y marcar el pago para reembolso cuando la cancelación se realiza con al menos `cancellation_deadline_hours` de antelación, y solo si el solicitante es el owner o ADMIN (RN-RES-04, RN-AUTH-02).

#### Scenario: USER cancela su reserva con suficiente antelación
- **WHEN** el owner envía `DELETE /api/reservas/{id}` de una reserva `CONFIRMED` con pago `PAID` y quedan más de `cancellation_deadline_hours` horas
- **THEN** el sistema responde 204
- **AND** la reserva pasa a `CANCELLED` y el pago a `REFUNDED` (o se inicia la devolución)

#### Scenario: USER intenta cancelar la reserva de otro usuario
- **WHEN** un USER que no es el owner envía `DELETE /api/reservas/{id}`
- **THEN** el sistema responde 403 con `code: "FORBIDDEN"` y la reserva no cambia de estado

### Requirement: Cancelar reserva fuera del plazo sin reembolso

El sistema SHALL impedir la cancelación con reembolso cuando se realiza con menos de `cancellation_deadline_hours` de antelación; el ADMIN SHALL poder forzar la cancelación como bypass de política (RN-RES-04, D-RES-03).

#### Scenario: USER intenta cancelar fuera del plazo
- **WHEN** el owner envía `DELETE /api/reservas/{id}` de una reserva `CONFIRMED` y quedan menos de `cancellation_deadline_hours` horas
- **THEN** el sistema responde 422 indicando que está fuera del plazo
- **AND** la reserva permanece `CONFIRMED`

#### Scenario: ADMIN cancela fuera del plazo (bypass)
- **WHEN** un ADMIN envía `PATCH /api/admin/reservas/{id}/estado` con `status: "CANCELLED"` sobre una reserva fuera de plazo
- **THEN** el sistema responde 200 con la reserva en `CANCELLED`
- **AND** la política de plazo no aplica a operaciones administrativas

### Requirement: Idempotencia en creación de reserva

El sistema SHALL devolver la reserva existente sin crear un duplicado cuando recibe una segunda petición `POST /api/reservas` con la misma `Idempotency-Key` para el mismo usuario, usando la tabla `idempotency_keys` con `UNIQUE(user_id, key)` (RN-RES-05).

#### Scenario: Segunda petición con la misma Idempotency-Key
- **WHEN** un usuario reenvía `POST /api/reservas` con la misma `Idempotency-Key: abc-123` que ya generó la reserva UUID-W
- **THEN** el sistema responde 201 con los mismos datos de UUID-W
- **AND** no se crea ninguna nueva reserva ni pago

#### Scenario: Idempotency-Key nueva crea una nueva reserva
- **WHEN** un usuario envía `POST /api/reservas` con `Idempotency-Key: xyz-999` para una franja libre
- **THEN** el sistema responde 201 creando una nueva reserva
- **AND** la pareja `(user_id, xyz-999)` queda registrada asociada a esa reserva

### Requirement: Confirmación y gestión administrativa de reservas

El sistema SHALL permitir al ADMIN listar todas las reservas y cambiar su estado, y SHALL aplicar la máquina de estados unidireccional `PENDING_CONFIRMATION → CONFIRMED → COMPLETED` con `→ CANCELLED` desde los dos primeros (D-RES-03, RN-AUTH-01).

#### Scenario: ADMIN confirma una reserva pendiente (camino CASH/MVP)
- **WHEN** un ADMIN envía `PATCH /api/admin/reservas/{id}/estado` con `status: "CONFIRMED"` sobre una reserva `PENDING_CONFIRMATION`
- **THEN** el sistema responde 200 con la reserva en `CONFIRMED`

#### Scenario: ADMIN lista todas las reservas
- **WHEN** un ADMIN envía `GET /api/admin/reservas`
- **THEN** el sistema responde 200 con todas las reservas del club

#### Scenario: USER no autorizado al listado admin
- **WHEN** un USER envía `GET /api/admin/reservas`
- **THEN** el sistema responde 403

#### Scenario: Transición de estado inválida
- **WHEN** un ADMIN intenta una transición no permitida (p. ej. `COMPLETED → CONFIRMED`)
- **THEN** el sistema responde 422 (las transiciones son unidireccionales)

### Requirement: Consulta de reservas propias con control de acceso

El sistema SHALL permitir a cada USER ver únicamente las reservas de las que es owner o participante, y SHALL responder 403 (nunca 404) al detalle de una reserva ajena para no revelar su existencia (RN-AUTH-01, prevención BOLA).

#### Scenario: USER lista sus reservas
- **WHEN** un USER envía `GET /api/reservas`
- **THEN** el sistema responde 200 solo con reservas donde es owner o participante

#### Scenario: USER accede al detalle de una reserva ajena
- **WHEN** un USER que no es owner ni participante envía `GET /api/reservas/{id}`
- **THEN** el sistema responde 403 (no 404)

#### Scenario: Petición sin autenticación
- **WHEN** se envía cualquier operación de reservas sin JWT válido
- **THEN** el sistema responde 401
