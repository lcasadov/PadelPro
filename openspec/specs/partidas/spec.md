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

### Requirement 4: Listado de partidas abiertas con identificador *(añadido — partidas-unirse)*

**El sistema DEBE exponer a un usuario autenticado, vía `GET /api/partidas?fecha=YYYY-MM-DD`, las partidas abiertas (reservas activas `PENDING_CONFIRMATION`/`CONFIRMED` con plazas libres) de la fecha, cada una identificable por `reservaId` y con hora, duración, plazas libres, participantes (nombre de display) e importe total (informativo). NO DEBE exponer datos sensibles (email/teléfono).**

#### Scenario: Ver partidas abiertas de una fecha
- **WHEN** un usuario autenticado consulta `GET /api/partidas?fecha=...` con reservas incompletas ese día
- **THEN** el sistema responde 200 con una lista de partidas, cada una con `reservaId`, hora, duración y plazas libres

#### Scenario: Reserva completa no aparece
- **WHEN** una reserva ya tiene `max_participants` participantes
- **THEN** no aparece en el listado de partidas abiertas

#### Scenario: Requiere autenticación
- **WHEN** se consulta el listado sin credenciales válidas
- **THEN** el sistema responde 401

### Requirement 5: Unión atómica de plaza bajo concurrencia *(añadido — partidas-unirse)*

**Al unirse vía `POST /api/reservas/{id}/unirse`, la comprobación de plazas y la inserción del participante DEBEN ser atómicas (bloqueo de la reserva, `SELECT ... FOR UPDATE`) para que uniones concurrentes no superen `max_participants`. El usuario que se une es siempre el sujeto del JWT (no un `userId` del cuerpo).**

#### Scenario: Última plaza en concurrencia
- **GIVEN** una reserva con una sola plaza libre
- **WHEN** varios usuarios envían `POST /api/reservas/{id}/unirse` simultáneamente
- **THEN** exactamente uno recibe 200 y el resto 422 `PARTICIPANTS_LIMIT_EXCEEDED`
- **AND** la reserva nunca supera `max_participants`

### Requirement 6: Abandonar una partida *(añadido — partidas-unirse)*

**El sistema DEBE permitir a un participante no-owner abandonar una reserva a la que se unió, vía `DELETE /api/reservas/{id}/participacion`, liberando su plaza. El owner NO DEBE poder abandonar (debe cancelar la reserva).**

#### Scenario: Participante no-owner abandona
- **GIVEN** un usuario autenticado participa (no-owner) en una reserva activa
- **WHEN** envía `DELETE /api/reservas/{id}/participacion`
- **THEN** el sistema responde 204, lo elimina de `participants` y libera la plaza

#### Scenario: El owner no puede abandonar
- **WHEN** el owner intenta `DELETE /api/reservas/{id}/participacion` sobre su propia reserva
- **THEN** el sistema responde 422 `OWNER_CANNOT_ABANDON` indicando que debe cancelar la reserva

### Requirement 7: Importe informativo y minimización de PII *(añadido — partidas-unirse)*

**La UI de confirmar unión DEBE mostrar el importe "tu parte" (total ÷ participantes tras la unión) como referencia informativa; la unión NO cobra online (cobro presencial; el pago compartido se difiere a `pagos-redsys`). Además, el detalle de reserva (`GET /api/reservas/{id}`) DEBE minimizar la PII para co-participantes no-owner: `externalPhone` de invitados y `notes` se devuelven `null` salvo para el owner o ADMIN (RN-RGPD-03).**

#### Scenario: Mostrar tu parte sin cobrar
- **WHEN** el usuario abre la confirmación de unión a una partida
- **THEN** la UI muestra el importe que le correspondería e indica que el pago es presencial (sin pasarela)

#### Scenario: Co-participante no ve teléfono ni notas ajenas
- **GIVEN** un usuario no-owner que se ha unido a una reserva con invitados externos y notas
- **WHEN** consulta `GET /api/reservas/{id}`
- **THEN** recibe `externalPhone` de los participantes y `notes` de la reserva en `null` (el owner y el ADMIN sí los ven)

## Casos límite
- Unirse a una reserva en estado `CANCELLED` o `COMPLETED`: el sistema responde 422 indicando que la reserva no admite nuevos participantes en ese estado.
- El `owner_id` de la reserva intenta unirse de nuevo via `/unirse`: el sistema responde 409 (ya es participante y owner).
- Unirse a una reserva en estado `PENDING_CONFIRMATION`: la openapi.yaml permite esta operación; la reserva no necesita estar `CONFIRMED` para admitir participantes adicionales.
- El mismo `user_id` ya registrado como participante externo (con `external_name`) e intenta unirse como usuario registrado: el sistema trata ambas entradas como distintas; aun así, si existe un registro `participants` con `user_id` coincidente, responde 409.

## Dependencias con otras capabilities
- **reservas**: Las partidas son un subconjunto de reservas. La lógica de creación, estado y cancelación de la reserva base pertenece a la capability `reservas`.
- **disponibilidad-pistas**: La consulta de disponibilidad (`GET /api/reservas/disponibles`) alimenta también la vista de partidas joinables.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 16 | Partidas abiertas | Mobile | USER | [`21-partidas-abiertas.html`](../../../docs/ux/mockups/21-partidas-abiertas.html) |
| 17 | Crear partida pública | Mobile | USER | [`20-crear-partida.html`](../../../docs/ux/mockups/20-crear-partida.html) |
| 18 | Confirmar unión | Mobile | USER | [`22-confirmar-union.html`](../../../docs/ux/mockups/22-confirmar-union.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de partida pública** — cubre el ciclo completo de una partida pública: listado de partidas abiertas (pantalla 16), creación de nueva partida (pantalla 17) y confirmación de unión a una partida existente (pantalla 18).

### Notas de UX

> - La pantalla de partidas abiertas (pantalla 16) solo muestra reservas con plazas libres (`participants_count < max_participants`); las partidas completas no aparecen.
> - Si el usuario ya es participante de una reserva e intenta unirse de nuevo, el sistema responde 409; la pantalla debe mostrar un mensaje claro sin redirigir al checkout (RN-AUTH-03).
> - La pantalla de confirmar unión (pantalla 18) debe mostrar la política de cancelación automática y el importe que corresponde al usuario antes de confirmar (RN-RES-04, RN-RES-02).
