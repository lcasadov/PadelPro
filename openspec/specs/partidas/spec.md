# Capability: partidas

## Resumen
Permite a los usuarios ver las reservas con plazas libres (partidas abiertas) y unirse a ellas como participantes adicionales, así como abandonar una reserva en la que ya participan. En PadelPro v1.0 no existe una entidad "partida" separada: una partida pública es una reserva en estado `CONFIRMED` o `PENDING_CONFIRMATION` con `participants_count < system_config.max_participants`. El endpoint `GET /api/reservas/disponibles` con el parámetro `soloDisponibles=true` filtra estas reservas joinables.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-AUTH-03**: Un USER no puede unirse dos veces a la misma reserva; el sistema debe detectar la duplicidad y responder 409.
- **RN-RES-02**: El máximo de participantes por reserva se lee de `system_config.max_participants` (default 4); si la reserva ya tiene el máximo de participantes, no se puede unir ninguno más.

## Entidades implicadas

| Entidad | Tabla | Rol |
|---|---|---|
| Reserva | `reservations` | La "partida abierta" es una reserva con plazas libres en status CONFIRMED o PENDING_CONFIRMATION |
| Participante | `participants` | Al unirse, se crea un nuevo registro con `slot_position` asignado, `is_owner=false`, `user_id` del usuario que se une |
| Usuario | `users` | Usuario que se une a la reserva; debe estar ACTIVE |
| Configuración del sistema | `system_config` | Fuente de `max_participants` para validar si quedan plazas |

## Endpoints
*(de docs/openapi.yaml)*

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reservas/disponibles` | Consultar disponibilidad (incluye tramos con plazas libres en reservas existentes) |
| `GET` | `/api/reservas` | Listar reservas del usuario autenticado (para ver en cuáles participa) |
| `POST` | `/api/reservas/{id}/unirse` | Unirse a una reserva existente como participante adicional |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Ver tramos con reservas joinables | Permitido | Permitido | Denegado (401) |
| Unirse a una reserva | Permitido | Permitido | Denegado (401) |
| Listar propias reservas (incluyendo en las que participa) | Permitido | Permitido | Denegado (401) |

---

## Requirements

### Requirement 1: Ver reservas con plazas libres

**El sistema DEBE devolver los tramos horarios en los que existen reservas con plazas disponibles cuando el usuario autenticado consulta la disponibilidad.**

#### Scenario: USER consulta tramos disponibles y ve una reserva con plazas libres
- **GIVEN** existe una reserva `CONFIRMED` para 2025-06-15 10:00–11:00 con 2 participantes (max=4)
- **AND** un usuario autenticado consulta `GET /api/reservas/disponibles?fecha=2025-06-15`
- **WHEN** la petición llega al sistema
- **THEN** el sistema responde 200
- **AND** el tramo 10:00 con `duracionMinutos: 60` aparece en `tramosDisponibles` con `plazasLibres: 2`

#### Scenario: USER no ve plazas libres cuando la reserva está completa
- **GIVEN** existe una reserva `CONFIRMED` para 2025-06-15 10:00–11:00 con 4 participantes (max=4)
- **WHEN** un usuario consulta `GET /api/reservas/disponibles?fecha=2025-06-15`
- **THEN** el tramo 10:00 no aparece en `tramosDisponibles` (0 plazas libres no se devuelve)

### Requirement 2: Unirse a una reserva con plazas disponibles

**El sistema DEBE añadir al usuario autenticado como participante en la reserva y responder 200 con los datos del nuevo participante, siempre que la reserva tenga plazas y el usuario no sea ya participante (RN-AUTH-03, RN-RES-02).**

#### Scenario: USER se une a una reserva con plazas libres
- **GIVEN** existe la reserva UUID-P con status `CONFIRMED`, 2 participantes y `max_participants=4`
- **AND** el usuario autenticado "usuarioC" no es participante de UUID-P
- **WHEN** "usuarioC" envía `POST /api/reservas/UUID-P/unirse`
- **THEN** el sistema responde 200 con `participanteId`, `reservaId`, `userId`, `nombre` y `statusPago`
- **AND** la reserva UUID-P tiene ahora 3 participantes
- **AND** se crea un registro en `participants` con `is_owner: false` para "usuarioC"

#### Scenario: USER intenta unirse a una reserva donde ya es participante (RN-AUTH-03)
- **GIVEN** el usuario autenticado "usuarioD" ya está en `participants` de la reserva UUID-Q
- **WHEN** "usuarioD" envía `POST /api/reservas/UUID-Q/unirse`
- **THEN** el sistema responde 409 con `code: "CONFLICT"` indicando que el usuario ya es participante
- **AND** no se crea un registro duplicado en `participants`

#### Scenario: USER intenta unirse a una reserva ya completa
- **GIVEN** la reserva UUID-R tiene `max_participants=4` y 4 participantes actuales
- **WHEN** un usuario autenticado envía `POST /api/reservas/UUID-R/unirse`
- **THEN** el sistema responde 422 indicando que la reserva está completa y no admite más participantes

#### Scenario: USER intenta unirse a una reserva que no existe
- **GIVEN** el UUID-INEXISTENTE no corresponde a ninguna reserva en el sistema
- **WHEN** un usuario envía `POST /api/reservas/UUID-INEXISTENTE/unirse`
- **THEN** el sistema responde 404 con `code: "NOT_FOUND"`

### Requirement 3: Listado de reservas en las que el usuario participa

**El sistema DEBE incluir en `GET /api/reservas` las reservas en las que el usuario autenticado aparece como participante (aunque no sea el owner), permitiéndole saber cuáles son sus partidas activas.**

#### Scenario: USER lista sus reservas y ve una en la que es participante no-owner
- **GIVEN** el usuario autenticado "usuarioE" está en `participants` de la reserva UUID-S como `is_owner: false`
- **AND** la reserva UUID-S tiene como `owner_id` a "usuarioF"
- **WHEN** "usuarioE" envía `GET /api/reservas`
- **THEN** el sistema responde 200
- **AND** UUID-S aparece en la lista de reservas del usuario
- **AND** el detalle de UUID-S muestra a "usuarioE" en la lista `participants` con `isOwner: false`

#### Scenario: USER no autenticado intenta ver reservas
- **GIVEN** una petición sin cabecera `Authorization`
- **WHEN** envía `POST /api/reservas/UUID-P/unirse`
- **THEN** el sistema responde 401 con `code: "UNAUTHORIZED"`

## Casos límite
- Unirse a una reserva en estado `CANCELLED` o `COMPLETED`: el sistema responde 422 indicando que la reserva no admite nuevos participantes en ese estado.
- El `owner_id` de la reserva intenta unirse de nuevo via `/unirse`: el sistema responde 409 (ya es participante y owner).
- Unirse a una reserva en estado `PENDING_CONFIRMATION`: la openapi.yaml permite esta operación; la reserva no necesita estar `CONFIRMED` para admitir participantes adicionales.
- El mismo `user_id` ya registrado como participante externo (con `external_name`) e intenta unirse como usuario registrado: el sistema trata ambas entradas como distintas; aun así, si existe un registro `participants` con `user_id` coincidente, responde 409.

## Dependencias con otras capabilities
- **reservas**: Las partidas son un subconjunto de reservas. La lógica de creación, estado y cancelación de la reserva base pertenece a la capability `reservas`.
- **disponibilidad-pistas**: La consulta de disponibilidad (`GET /api/reservas/disponibles`) alimenta también la vista de partidas joinables.
