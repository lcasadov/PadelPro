# Capability: disponibilidad-pistas

## Resumen
Consulta de franjas horarias libres de la pista para una fecha concreta. Permite a los usuarios autenticados conocer qué horas tienen plazas disponibles antes de crear o unirse a una reserva. El cálculo tiene en cuenta las reservas activas (`status <> 'CANCELLED'`) que solapan la franja, el estado operativo de la pista y el número máximo de participantes configurado. Los resultados se cachean 30 segundos e se invalidan automáticamente al crearse o cancelarse una reserva.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-RES-01**: El anti-solapamiento de franjas se basa en el mismo rango horario que la consulta de disponibilidad; un tramo solo aparece como libre si ninguna reserva activa lo ocupa completamente.
- **RN-RES-02**: Un tramo con reserva existente pero con plazas libres (`participants_count < max_participants`) aparece en la respuesta con las plazas restantes.
- **RN-RES-04**: Un tramo cubierto por una pista en estado MANTENIMIENTO no aparece en `tramosDisponibles` (0 plazas disponibles).

## Entidades implicadas

| Entidad | Tabla | Rol |
|---|---|---|
| Reserva | `reservations` | Fuente de franjas ocupadas; se filtran las que tienen `status <> 'CANCELLED'` |
| Participante | `participants` | Permite contar cuántas plazas de una franja con reserva existente están ocupadas |
| Configuración del sistema | `system_config` | Fuente de `max_participants` (para calcular plazas libres) y estado operativo de la pista |

## Endpoints
*(de docs/openapi.yaml)*

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reservas/disponibles` | Consultar tramos horarios con plazas libres para una fecha concreta |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Consultar disponibilidad (`GET /api/reservas/disponibles`) | Permitido | Permitido | Denegado (401) |

---

## Requirements

### Requirement 1: Devolver tramos libres para una fecha sin reservas activas

**El sistema DEBE devolver todos los tramos horarios del día como disponibles cuando no existe ninguna reserva activa para esa fecha.**

#### Scenario: Consulta de un día sin ninguna reserva activa
- **GIVEN** un usuario autenticado (USER o ADMIN)
- **AND** no existe ninguna reserva con `reservation_date = 2025-08-01` y `status <> 'CANCELLED'`
- **WHEN** envía `GET /api/reservas/disponibles?fecha=2025-08-01`
- **THEN** el sistema responde 200 con la fecha `2025-08-01` y la lista `tramosDisponibles` con todos los tramos del horario del club
- **AND** cada tramo tiene `plazasLibres` igual a `system_config.max_participants`

### Requirement 2: Devolver tramos con plazas parciales cuando existe una reserva incompleta

**El sistema DEBE devolver el tramo horario con el número correcto de plazas libres cuando existe una reserva activa en ese tramo con menos participantes que el máximo.**

#### Scenario: Consulta de un día con una reserva parcialmente ocupada
- **GIVEN** existe la reserva UUID-D con `reservation_date=2025-08-05`, `start_time=10:00`, `duration_minutes=60`, `status=CONFIRMED` y 2 participantes
- **AND** `system_config.max_participants = 4`
- **WHEN** un usuario autenticado envía `GET /api/reservas/disponibles?fecha=2025-08-05`
- **THEN** el sistema responde 200
- **AND** el tramo `horaInicio: "10:00"` con `duracionMinutos: 60` aparece en `tramosDisponibles` con `plazasLibres: 2`

#### Scenario: Consulta de un día con múltiples reservas en distintos tramos
- **GIVEN** existen reservas `CONFIRMED` en 09:00–10:00 (3 participantes) y 11:00–12:00 (4 participantes) para 2025-08-10
- **AND** el tramo 10:00–11:00 no tiene ninguna reserva activa
- **WHEN** un usuario envía `GET /api/reservas/disponibles?fecha=2025-08-10`
- **THEN** el tramo 09:00 aparece con `plazasLibres: 1`
- **AND** el tramo 10:00 aparece con `plazasLibres: 4` (sin reserva)
- **AND** el tramo 11:00 NO aparece (0 plazas libres)

### Requirement 3: Devolver lista vacía cuando la pista está en mantenimiento

**El sistema DEBE devolver `tramosDisponibles` vacío para cualquier fecha cuando la pista está en estado MANTENIMIENTO, sin revelar el motivo en la respuesta de disponibilidad.**

#### Scenario: Consulta de disponibilidad con pista en mantenimiento
- **GIVEN** la configuración del sistema indica que la pista está en estado MANTENIMIENTO (via `system_config`)
- **WHEN** un usuario autenticado envía `GET /api/reservas/disponibles?fecha=2025-09-01`
- **THEN** el sistema responde 200 con `tramosDisponibles: []`
- **AND** la respuesta no incluye ningún campo que revele explícitamente que el motivo es MANTENIMIENTO (para no exponer información operativa innecesaria)

#### Scenario: Consulta con parámetro `fecha` en formato inválido
- **GIVEN** un usuario autenticado
- **WHEN** envía `GET /api/reservas/disponibles?fecha=15-06-2025` (formato incorrecto, no YYYY-MM-DD)
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`
- **AND** el mensaje de error indica que el formato de fecha debe ser `YYYY-MM-DD`

#### Scenario: Consulta sin el parámetro `fecha` requerido
- **GIVEN** un usuario autenticado
- **WHEN** envía `GET /api/reservas/disponibles` sin el parámetro `fecha`
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`

## Casos límite
- Fecha en el pasado: el sistema puede responder con los tramos (histórico) o con 400; en v1.0 se acepta responder 200 con los tramos históricos para facilitar la consulta del ADMIN, pero no se garantiza exactitud si los datos fueron modificados.
- Fecha fuera del rango de la ventana de reserva anticipada (`system_config`): el sistema puede devolver tramos pero la creación de reserva para esa fecha fallará; la disponibilidad no filtra por ventana anticipada.
- Reservas en estado `PENDING_CONFIRMATION` se tratan igual que `CONFIRMED` para el cálculo de disponibilidad: bloquean franjas y reducen plazas libres.
- El endpoint usa caché de 30 segundos con clave `fecha`; los cambios de reservas en el mismo segundo pueden no reflejarse inmediatamente.
- Si `system_config.max_participants` cambia, la caché se invalida y el siguiente request refleja el nuevo valor.
- El endpoint requiere autenticación (JWT válido); una petición con token expirado recibe 401, no 200 con datos.

## Dependencias con otras capabilities
- **reservas**: La disponibilidad se calcula a partir de las reservas activas; cada creación o cancelación de reserva invalida la caché de `available-slots` para la fecha afectada.
- **partidas**: La vista de partidas joinables se basa en la misma consulta de disponibilidad, filtrando los tramos con `plazasLibres > 0` asociados a reservas existentes con huecos.
- **pistas**: El estado operativo de la pista (MANTENIMIENTO) afecta directamente al resultado de disponibilidad; se lee de `system_config`.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 06 | Home jugador | Mobile | USER | [`02-home-jugador.html`](../../../docs/ux/mockups/02-home-jugador.html) |
| 07 | Buscar disponibilidad | Mobile | USER | [`03-buscar-disponibilidad.html`](../../../docs/ux/mockups/03-buscar-disponibilidad.html) |
| 22 | Calendario semanal | Desktop | ADMIN | [`18-calendario-semanal.html`](../../../docs/ux/mockups/18-calendario-semanal.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de reserva de pista con pago Redsys** — la pantalla de búsqueda de disponibilidad (pantalla 07) es la puerta de entrada al flujo de reserva; el resultado de disponibilidad determina qué franjas horarias puede seleccionar el usuario.
- **Flujo admin — gestión del club** — el calendario semanal (pantalla 22) visualiza la ocupación de las franjas horarias, construida sobre la misma lógica de disponibilidad.

### Notas de UX

> - Cuando la pista está en mantenimiento, la pantalla de búsqueda de disponibilidad (pantalla 07) debe mostrar lista vacía sin revelar el motivo (RN-RES-04); el mensaje genérico es suficiente.
> - El parámetro `fecha` debe validarse en formato YYYY-MM-DD; un formato incorrecto muestra error 400 antes de renderizar resultados.
