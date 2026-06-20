# Capability: reservas

## Resumen
Gestión del ciclo de vida completo de una reserva de pista: creación, consulta, cambio de estado y cancelación. Es la capability central del sistema. Implementa las barreras de anti-solapamiento de franjas horarias, la idempotencia por `Idempotency-Key`, el cálculo de precio en backend y las políticas de cancelación configurables. Toda reserva nace atómica junto con su registro de pago asociado.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-AUTH-01**: USER solo puede leer su propia reserva si es `owner_id` o aparece en `participants`.
- **RN-AUTH-02**: USER solo puede cancelar la reserva de la que es `owner_id`.
- **RN-RES-01**: Sin solapamiento de franja en la misma pista; doble barrera: `SELECT FOR UPDATE` en Application + `EXCLUDE USING gist` en BD.
- **RN-RES-02**: Máximo de participantes por reserva leído de `system_config.max_participants` (default 4).
- **RN-RES-03**: El precio total se calcula exclusivamente en backend (`price_per_hour × duration_minutes / 60`); el importe del cliente es ignorado.
- **RN-RES-04**: La ventana de cancelación anticipada y la política de reembolso se leen de `system_config.cancellation_deadline_hours`.
- **RN-RES-05**: Idempotencia: un `POST /api/reservas` con la misma `Idempotency-Key` devuelve la reserva existente sin crear un duplicado.
- **RN-RGPD-03**: Los errores no exponen datos personales de otros usuarios.
- **RN-RGPD-04**: Los logs no almacenan contraseñas, tokens, OTPs ni datos de tarjeta.

## Entidades implicadas

| Entidad | Tabla | Rol |
|---|---|---|
| Reserva | `reservations` | Entidad principal: fecha, hora, duración, estado, canal, titular |
| Participante | `participants` | Jugadores asignados a la reserva (1..4); el titular ocupa `slot_position=1` con `is_owner=true` |
| Pago | `payments` | Creado atómicamente con la reserva; `status=PENDING`, `amount` congelado en el momento de crear |
| Configuración del sistema | `system_config` | Fuente de `price_per_hour`, `max_participants`, `cancellation_deadline_hours` |
| Usuario | `users` | Titular (`owner_id`) y participantes registrados |
| Código OTP | `otp_codes` | Código `RESERVATION_CONFIRM` o `CANCELLATION_CONFIRM` para operaciones críticas vía Telegram |

## Endpoints
*(de docs/openapi.yaml)*

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reservas` | Listar reservas del usuario autenticado (owner o participante) |
| `POST` | `/api/reservas` | Crear nueva reserva |
| `GET` | `/api/reservas/{id}` | Obtener detalle de una reserva |
| `DELETE` | `/api/reservas/{id}` | Cancelar reserva (solo el owner o ADMIN) |
| `GET` | `/api/admin/reservas` | Listar todas las reservas (ADMIN) |
| `PATCH` | `/api/admin/reservas/{id}/estado` | Cambiar estado de reserva (ADMIN) |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Listar reservas propias (`GET /api/reservas`) | Permitido (ve todas) | Permitido (solo propias) | Denegado (401) |
| Crear reserva (`POST /api/reservas`) | Permitido | Permitido | Denegado (401) |
| Ver detalle de reserva (`GET /api/reservas/{id}`) | Permitido (cualquiera) | Permitido (solo si owner o participante) | Denegado (401) |
| Cancelar reserva (`DELETE /api/reservas/{id}`) | Permitido (cualquiera) | Permitido (solo si owner) | Denegado (401) |
| Listar todas las reservas (`GET /api/admin/reservas`) | Permitido | Denegado (403) | Denegado (401) |
| Cambiar estado de reserva (`PATCH /api/admin/reservas/{id}/estado`) | Permitido | Denegado (403) | Denegado (401) |

---

## Requirements

### Requirement 1: Crear reserva en franja libre

**El sistema DEBE crear la reserva con estado `PENDING_CONFIRMATION` cuando la franja horaria solicitada está libre, el usuario es ACTIVE, y los datos de la petición son válidos.**

#### Scenario: USER crea reserva en franja libre con datos válidos
- **GIVEN** un usuario con rol USER y estado ACTIVE autenticado
- **AND** no existe ninguna reserva activa (`status <> 'CANCELLED'`) que solape la franja 2025-06-15 09:00–10:00
- **WHEN** envía `POST /api/reservas` con `reservationDate: "2025-06-15"`, `startTime: "09:00"`, `durationMinutes: 60`
- **THEN** el sistema responde 201 con la reserva en estado `PENDING_CONFIRMATION`
- **AND** el campo `priceTotal` refleja `system_config.price_per_hour × (60/60)` calculado en backend
- **AND** el creador aparece como participante con `is_owner: true` y `slot_position: 1`
- **AND** se crea atómicamente un registro en `payments` con `status=PENDING` y el mismo `amount`

#### Scenario: USER crea reserva con participante adicional externo
- **GIVEN** un usuario con rol USER y estado ACTIVE autenticado
- **AND** la franja 2025-06-20 11:00–12:30 está libre
- **WHEN** envía `POST /api/reservas` con `durationMinutes: 90` y `participantesAdicionales: [{externalName: "Carlos García"}]`
- **THEN** el sistema responde 201 con la reserva en estado `PENDING_CONFIRMATION`
- **AND** la lista `participants` contiene 2 entradas: el titular (`is_owner: true`) y el participante externo

#### Scenario: Datos de entrada inválidos (fecha pasada)
- **GIVEN** un usuario autenticado
- **WHEN** envía `POST /api/reservas` con `reservationDate` igual a una fecha en el pasado
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`

### Requirement 2: Rechazo de reserva por conflicto de horario

**El sistema DEBE rechazar la creación de una reserva con 409 cuando ya existe una reserva activa que solapa la misma franja horaria (RN-RES-01).**

#### Scenario: Conflicto de horario directo
- **GIVEN** existe una reserva activa en la franja 2025-06-15 09:00–10:00 con `status=CONFIRMED`
- **WHEN** un usuario envía `POST /api/reservas` con `reservationDate: "2025-06-15"`, `startTime: "09:00"`, `durationMinutes: 60`
- **THEN** el sistema responde 409 con `code: "CONFLICT"` y mensaje indicando que la franja está ocupada
- **AND** no se crea ninguna nueva reserva ni registro de pago

#### Scenario: Conflicto de horario parcial (solapamiento de inicio)
- **GIVEN** existe una reserva activa en la franja 2025-06-15 08:00–09:30 con `status=CONFIRMED`
- **WHEN** un usuario envía `POST /api/reservas` con `startTime: "09:00"`, `durationMinutes: 60` (franja 09:00–10:00 solapa con 08:00–09:30)
- **THEN** el sistema responde 409 con `code: "CONFLICT"`

#### Scenario: Concurrencia — dos usuarios intentan la misma franja simultáneamente
- **GIVEN** la franja 2025-06-15 10:00–11:00 está libre
- **AND** dos usuarios envían `POST /api/reservas` para esa franja simultáneamente
- **THEN** exactamente uno recibe 201 y el otro recibe 409
- **AND** el constraint `EXCLUDE USING gist` de la BD garantiza que no se generan dos reservas solapadas bajo ninguna condición de carrera

### Requirement 3: Cancelar reserva dentro del plazo — con reembolso

**El sistema DEBE cancelar la reserva y marcar el pago para reembolso cuando la cancelación se realiza con al menos `cancellation_deadline_hours` de antelación (RN-RES-04).**

#### Scenario: USER cancela su propia reserva con suficiente antelación
- **GIVEN** un usuario con rol USER es `owner_id` de la reserva UUID-X con status `CONFIRMED`
- **AND** quedan más de `system_config.cancellation_deadline_hours` horas hasta `reservation_date + start_time`
- **AND** el pago asociado está en estado `PAID`
- **WHEN** envía `DELETE /api/reservas/UUID-X`
- **THEN** el sistema responde 204
- **AND** la reserva pasa a `status: CANCELLED`
- **AND** el pago asociado pasa a `status: REFUNDED` (o se inicia el proceso de devolución Redsys)

#### Scenario: USER intenta cancelar la reserva de otro usuario
- **GIVEN** un usuario con rol USER autenticado como "usuarioB"
- **AND** la reserva UUID-X tiene `owner_id` correspondiente a "usuarioA"
- **WHEN** "usuarioB" envía `DELETE /api/reservas/UUID-X`
- **THEN** el sistema responde 403 con `code: "FORBIDDEN"` (RN-AUTH-02)
- **AND** la reserva no cambia de estado

### Requirement 4: Cancelar reserva fuera del plazo — sin reembolso

**El sistema DEBE cancelar la reserva sin generar reembolso cuando la cancelación se realiza con menos de `cancellation_deadline_hours` de antelación (RN-RES-04).**

#### Scenario: USER intenta cancelar fuera del plazo permitido
- **GIVEN** un usuario con rol USER es `owner_id` de la reserva UUID-Y con status `CONFIRMED`
- **AND** quedan menos de `system_config.cancellation_deadline_hours` horas hasta la reserva
- **WHEN** envía `DELETE /api/reservas/UUID-Y`
- **THEN** el sistema responde 422 con mensaje indicando que la cancelación está fuera del plazo permitido
- **AND** la reserva permanece en estado `CONFIRMED`

#### Scenario: ADMIN cancela una reserva fuera del plazo (bypass de política)
- **GIVEN** un usuario con rol ADMIN autenticado
- **AND** la reserva UUID-Z tiene status `CONFIRMED` y la cancelación está fuera del plazo
- **WHEN** el ADMIN envía `PATCH /api/admin/reservas/UUID-Z/estado` con `status: "CANCELLED"`
- **THEN** el sistema responde 200 con la reserva en estado `CANCELLED`
- **AND** la política de plazo no aplica a operaciones administrativas

### Requirement 5: Idempotencia en creación de reserva

**El sistema DEBE devolver la reserva existente sin crear un duplicado cuando recibe una segunda petición `POST /api/reservas` con la misma `Idempotency-Key` (RN-RES-05).**

#### Scenario: Segunda petición con la misma Idempotency-Key
- **GIVEN** un usuario autenticado envió `POST /api/reservas` con cabecera `Idempotency-Key: abc-123` y recibió 201 con la reserva UUID-W
- **WHEN** el mismo usuario reenvía `POST /api/reservas` con la misma `Idempotency-Key: abc-123` (ej. reintento de red)
- **THEN** el sistema responde 201 con los mismos datos de la reserva UUID-W
- **AND** no se crea ninguna nueva reserva ni nuevo pago

#### Scenario: Petición con Idempotency-Key nueva crea una nueva reserva
- **GIVEN** un usuario autenticado
- **WHEN** envía `POST /api/reservas` con `Idempotency-Key: xyz-999` para una franja libre
- **THEN** el sistema responde 201 creando una nueva reserva
- **AND** la `Idempotency-Key: xyz-999` queda registrada asociada a esa reserva

### Requirement 6: Confirmación y gestión administrativa de reservas

**El sistema DEBE permitir al ADMIN listar todas las reservas y cambiar su estado, aplicando la máquina de estados unidireccional `PENDING_CONFIRMATION → CONFIRMED → COMPLETED` con `→ CANCELLED` desde los dos primeros (D-RES-03, RN-AUTH-01).**

#### Scenario: ADMIN confirma una reserva pendiente (camino CASH/MVP)
- **GIVEN** un usuario con rol ADMIN autenticado
- **WHEN** envía `PATCH /api/admin/reservas/{id}/estado` con `status: "CONFIRMED"` sobre una reserva `PENDING_CONFIRMATION`
- **THEN** el sistema responde 200 con la reserva en `CONFIRMED`

#### Scenario: ADMIN lista todas las reservas
- **GIVEN** un usuario con rol ADMIN autenticado
- **WHEN** envía `GET /api/admin/reservas`
- **THEN** el sistema responde 200 con todas las reservas del club

#### Scenario: USER no autorizado al listado admin
- **WHEN** un usuario con rol USER envía `GET /api/admin/reservas`
- **THEN** el sistema responde 403

#### Scenario: Transición de estado inválida
- **WHEN** un ADMIN intenta una transición no permitida (ej. `COMPLETED → CONFIRMED`)
- **THEN** el sistema responde 422 (las transiciones son unidireccionales)

### Requirement 7: Consulta de reservas propias con control de acceso

**El sistema DEBE permitir a cada USER ver únicamente las reservas de las que es owner o participante, y DEBE responder 403 (nunca 404) al detalle de una reserva ajena para no revelar su existencia (RN-AUTH-01, prevención BOLA).**

#### Scenario: USER lista sus reservas
- **WHEN** un USER envía `GET /api/reservas`
- **THEN** el sistema responde 200 solo con reservas donde es owner o participante

#### Scenario: USER accede al detalle de una reserva ajena
- **WHEN** un USER que no es owner ni participante envía `GET /api/reservas/{id}`
- **THEN** el sistema responde 403 (no 404)

#### Scenario: Petición sin autenticación
- **WHEN** se envía cualquier operación de reservas sin JWT válido
- **THEN** el sistema responde 401

## Casos límite
- Reserva con `durationMinutes` no incluido en `[60, 90, 120, 150, 180]`: el sistema responde 400.
- Reserva con `startTime` que no es en punto ni en media hora (ej. `09:15`): el sistema responde 400.
- Reserva con más de `system_config.max_participants - 1` participantes adicionales (el titular ocupa slot 1): el sistema responde 422 con `PARTICIPANTS_LIMIT_EXCEEDED`.
- Cancelar una reserva ya en estado `CANCELLED`: el sistema responde 422 indicando que la transición no está permitida.
- Cancelar una reserva con `status: PENDING_CONFIRMATION` y pago `IN_PROGRESS`: el sistema responde 422 hasta que el pago se resuelva, o el ADMIN puede forzar vía endpoint admin.
- `GET /api/reservas/{id}` por un USER que no es ni owner ni participante: responde 403, nunca 404, para no revelar existencia del recurso (BOLA prevention).
- Transición de estado `COMPLETED → CONFIRMED` intentada por ADMIN: el sistema responde 422; las transiciones son unidireccionales.
- Máximo de participantes desde `system_config`: si se cambia `max_participants` a 3, las reservas existentes con 4 participantes no se ven afectadas; solo las nuevas aplican el nuevo límite.

## Dependencias con otras capabilities
- **pistas**: La creación de reservas verifica disponibilidad de la pista; el estado MANTENIMIENTO bloquea nuevas reservas.
- **disponibilidad-pistas**: La disponibilidad se calcula a partir de las reservas activas en `reservations`.
- **partidas**: Las reservas con plazas libres y status `CONFIRMED` o `PENDING_CONFIRMATION` son joinables desde la capability `partidas`.
- **pagos-redsys**: Cada reserva crea atómicamente un registro de pago; el flujo de pago Redsys referencia el `payments.id` y el `reservations.id`.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 06 | Home jugador | Mobile | USER | [`02-home-jugador.html`](../../../docs/ux/mockups/02-home-jugador.html) |
| 08 | Confirmar reserva | Mobile | USER | [`04-confirmar-reserva.html`](../../../docs/ux/mockups/04-confirmar-reserva.html) |
| 10 | Pago confirmado | Mobile | USER | [`12-pago-confirmado.html`](../../../docs/ux/mockups/12-pago-confirmado.html) |
| 11 | Mis reservas | Mobile | USER | [`05-mis-reservas.html`](../../../docs/ux/mockups/05-mis-reservas.html) |
| 12 | Detalle de reserva | Mobile | USER | [`13-detalle-reserva.html`](../../../docs/ux/mockups/13-detalle-reserva.html) |
| 22 | Calendario semanal | Desktop | ADMIN | [`18-calendario-semanal.html`](../../../docs/ux/mockups/18-calendario-semanal.html) |
| 23 | Reservas del club | Desktop | ADMIN | [`19-reservas-club.html`](../../../docs/ux/mockups/19-reservas-club.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de reserva de pista con pago Redsys** — es la capability central del flujo: cubre la confirmación de la reserva (pantalla 08), la pantalla de pago confirmado (pantalla 10) y el detalle posterior de la reserva (pantalla 12).
- **Flujo admin — gestión del club** — el calendario semanal (pantalla 22) y la tabla de reservas del club (pantalla 23) permiten al ADMIN supervisar y gestionar el estado de todas las reservas.

### Notas de UX

> - El importe mostrado en la pantalla de confirmar reserva (pantalla 08) proviene siempre del backend; el cliente no puede modificarlo (RN-RES-03).
> - Un USER solo ve en Mis reservas (pantalla 11) las reservas de las que es owner o participante (RN-AUTH-01).
> - La pantalla de detalle de reserva (pantalla 12) debe mostrar el botón de cancelación solo si el usuario es el owner y la reserva está en un estado cancelable; fuera del plazo de cancelación debe indicar visualmente que no aplica reembolso (RN-RES-04).
