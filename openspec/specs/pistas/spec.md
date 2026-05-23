# Capability: pistas

## Resumen
Gestión del catálogo de pistas (courts) del club. Permite al ADMIN crear, consultar y cambiar el estado de las pistas (ACTIVA / MANTENIMIENTO). Los USERs pueden listar las pistas disponibles para hacer reservas. Una pista en estado MANTENIMIENTO no admite nuevas reservas, pero las reservas preexistentes no se cancelan automáticamente.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-AUTH-01**: Un USER solo puede consultar pistas; nunca crear, modificar ni desactivar.
- **RN-RES-01**: Las pistas son la unidad sobre la que se valida el anti-solapamiento de franjas horarias.
- **RN-RES-04**: Una pista en estado MANTENIMIENTO bloquea la creación de nuevas reservas sobre ella; las reservas activas preexistentes se mantienen.

## Entidades implicadas

| Entidad | Tabla | Rol |
|---|---|---|
| `system_config` | `system_config` | Fuente de `max_participants` y precio por hora que aplica a las reservas de la pista |
| `reservations` | `reservations` | Las reservas referencian la pista implícitamente (una sola pista en v1.0) |

> **Nota de modelado v1.0:** En la versión actual PadelPro gestiona una única pista física. El concepto de "pista" está presente en el API a través de la disponibilidad y las reglas de solapamiento (`excl_res_no_overlap`), pero no existe una tabla `courts` separada. Los endpoints de gestión de pistas son los de disponibilidad y configuración del sistema. Esta spec documenta el comportamiento de "gestión de la pista única" tal como se expone a través del API.

## Endpoints
*(de docs/openapi.yaml)*

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reservas/disponibles` | Consultar disponibilidad de la pista (tramos libres por fecha) |
| `GET` | `/api/admin/sistema/config` | Obtener configuración del sistema, incluyendo estado operativo (ADMIN) |
| `PATCH` | `/api/admin/sistema/config` | Actualizar configuración del sistema (incluye política de pista) (ADMIN) |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Consultar disponibilidad de la pista | Permitido | Permitido | Denegado (401) |
| Obtener configuración del sistema | Permitido | Denegado (403) | Denegado (401) |
| Actualizar configuración del sistema | Permitido | Denegado (403) | Denegado (401) |

---

## Requirements

### Requirement 1: Consulta de disponibilidad de la pista

**El sistema DEBE devolver los tramos horarios con plazas libres para una fecha concreta cuando un usuario autenticado los solicite.**

#### Scenario: USER consulta disponibilidad en un día con franjas libres
- **GIVEN** un usuario con rol USER autenticado
- **WHEN** envía `GET /api/reservas/disponibles?fecha=2025-06-15`
- **THEN** el sistema responde 200 con la lista de `tramosDisponibles`, cada uno con `horaInicio`, `duracionMinutos` y `plazasLibres >= 1`

#### Scenario: USER consulta disponibilidad en un día completamente ocupado
- **GIVEN** un usuario con rol USER autenticado
- **AND** todas las franjas del día 2025-06-16 tienen reservas activas (status CONFIRMED o PENDING_CONFIRMATION) que cubren el horario completo del club
- **WHEN** envía `GET /api/reservas/disponibles?fecha=2025-06-16`
- **THEN** el sistema responde 200 con `tramosDisponibles` vacío (`[]`)

#### Scenario: Usuario no autenticado intenta consultar disponibilidad
- **GIVEN** una petición sin cabecera `Authorization`
- **WHEN** envía `GET /api/reservas/disponibles?fecha=2025-06-15`
- **THEN** el sistema responde 401

### Requirement 2: Restricción de modificación de configuración a ADMIN

**El sistema DEBE denegar a cualquier usuario con rol USER el acceso a los endpoints de configuración del sistema y devolver 403.**

#### Scenario: USER intenta acceder a configuración del sistema
- **GIVEN** un usuario con rol USER autenticado con token JWT válido
- **WHEN** envía `GET /api/admin/sistema/config`
- **THEN** el sistema responde 403 con `code: "FORBIDDEN"`

#### Scenario: USER intenta modificar la configuración del sistema
- **GIVEN** un usuario con rol USER autenticado
- **WHEN** envía `PATCH /api/admin/sistema/config` con body `{"pricePerHour": 5.00}`
- **THEN** el sistema responde 403

#### Scenario: ADMIN actualiza configuración del sistema con datos válidos
- **GIVEN** un usuario con rol ADMIN autenticado
- **WHEN** envía `PATCH /api/admin/sistema/config` con body `{"maxParticipants": 4, "cancellationDeadlineHours": 2}`
- **THEN** el sistema responde 200 con la configuración actualizada
- **AND** los campos `maxParticipants` y `cancellationDeadlineHours` reflejan los nuevos valores

### Requirement 3: Bloqueo de nuevas reservas cuando la pista está en mantenimiento

**El sistema DEBE rechazar la creación de nuevas reservas cuando la configuración de la pista indica estado MANTENIMIENTO, sin cancelar las reservas preexistentes.**

#### Scenario: ADMIN deshabilita nuevas reservas poniendo la pista en mantenimiento
- **GIVEN** existen reservas CONFIRMED para la fecha 2025-07-01
- **AND** un usuario con rol ADMIN cambia el estado operativo de la pista a MANTENIMIENTO via `PATCH /api/admin/sistema/config`
- **WHEN** un USER intenta `POST /api/reservas` para el 2025-07-01
- **THEN** el sistema responde 422 indicando que la pista no está disponible para nuevas reservas
- **AND** las reservas CONFIRMED preexistentes para el 2025-07-01 permanecen sin cambio

#### Scenario: Consulta de disponibilidad con pista en mantenimiento
- **GIVEN** la pista está en estado MANTENIMIENTO
- **WHEN** un USER envía `GET /api/reservas/disponibles?fecha=2025-07-01`
- **THEN** el sistema responde 200 con `tramosDisponibles` vacío (`[]`)
- **AND** la respuesta no expone internamente el motivo (mantenimiento vs. ocupación completa) para no revelar información operativa

## Casos límite
- Consulta de disponibilidad para una fecha en el pasado: el sistema puede responder 200 con lista vacía o 400 según la validación de fecha configurada; en v1.0 se acepta 400.
- Consulta con `fecha` en formato inválido (ej. `2025-13-40`): el sistema responde 400 con `code: "VALIDATION_ERROR"`.
- Actualización de `system_config` con `maxParticipants` fuera del rango 1-4: el sistema responde 400.
- Actualización de `system_config` con `pricePerHour <= 0`: el sistema responde 400.
- Los campos sensibles (`redsys_secret_key`, `telegram_bot_token`, `smtp_password`) nunca se devuelven en claro en la respuesta de `GET /api/admin/sistema/config` (RN-RGPD-03).

## Dependencias con otras capabilities
- **reservas**: La disponibilidad de la pista es prerrequisito para crear reservas. La capability `reservas` consume los tramos devueltos por `/api/reservas/disponibles`.
- **disponibilidad-pistas**: Esta capability es la vista de consulta de disponibilidad; `pistas` es el plano de configuración que la controla.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 24 | Gestión de pistas | Desktop | ADMIN | [`17-gestion-pistas.html`](../../../docs/ux/mockups/17-gestion-pistas.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo admin — gestión del club** — la pantalla de gestión de pistas (pantalla 24) permite al ADMIN ver el estado operativo (Activa / Mantenimiento) y las métricas de ocupación y tarifa de la pista; desde aquí se controla si la pista acepta nuevas reservas.

### Notas de UX

> - Cuando la pista pasa a estado MANTENIMIENTO, la pantalla debe advertir que las reservas existentes no se cancelan automáticamente; solo se bloquean nuevas reservas (RN-RES-04).
> - Los campos sensibles de `system_config` (claves Redsys, token Telegram, contraseña SMTP) nunca se muestran en claro en la pantalla de configuración (RN-SEC-02).
