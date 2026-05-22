# Capability: roles-permisos

## Resumen

Define y hace cumplir la matriz de control de acceso basada en roles (RBAC) de PadelPro v1.0. Gestiona la asignación del rol `ADMIN` o `USER` a cada cuenta, protege todos los endpoints bajo `/api/admin/**` con verificación de rol en la capa de seguridad, e implementa las reglas de autorización fina que van más allá del rol (verificación de propiedad de recursos). Esta capability es transversal: todas las demás capabilities dependen de ella.

## Fase

Fase 1

## Reglas de negocio implicadas

- **RN-AUTH-01**: Un USER solo puede ver una reserva si es `owner_id` o aparece en `participants`. La verificación ocurre en la capa Application (no en el Controller).
- **RN-AUTH-02**: Un USER solo puede cancelar su propia reserva (`owner_id = authenticatedUserId`).
- **RN-AUTH-03**: Un USER no puede unirse dos veces a la misma reserva.
- **RN-AUTH-04**: Un USER solo puede iniciar pago de una reserva de la que es `owner_id`.
- **RN-AUTH-05**: Un ADMIN no puede desactivarse a sí mismo.

## Entidades implicadas

**users** (tabla `users`):
- `id`, `role` (`ADMIN|USER`), `status` (`PENDING|ACTIVE|INACTIVE`)

## Endpoints

Los endpoints de esta capability no tienen rutas propias; la protección se aplica de forma transversal a todos los endpoints del sistema.

**Endpoints protegidos exclusivamente para ADMIN:**

| Método | Path |
|---|---|
| `GET` | `/api/admin/usuarios` |
| `POST` | `/api/admin/usuarios` |
| `GET` | `/api/admin/usuarios/{id}` |
| `PATCH` | `/api/admin/usuarios/{id}` |
| `PATCH` | `/api/admin/usuarios/{id}/aprobar` |
| `DELETE` | `/api/admin/usuarios/{id}` |
| `GET` | `/api/admin/reservas` |
| `PATCH` | `/api/admin/reservas/{id}/estado` |
| `POST` | `/api/admin/pagos/{reservaId}/efectivo` |
| `GET` | `/api/admin/pagos` |
| `GET` | `/api/admin/sistema/config` |
| `PATCH` | `/api/admin/sistema/config` |

**Endpoints de asignación de rol** (operación incluida en `PATCH /api/admin/usuarios/{id}`):

| Método | Path | Rol requerido |
|---|---|---|
| `PATCH` | `/api/admin/usuarios/{id}` | ADMIN — modifica campo `role` |

## Permisos

### Matriz RBAC completa (Fase 1)

| Recurso / Acción | No autenticado | USER | ADMIN |
|---|---|---|---|
| `POST /api/auth/login` | Permitido | Permitido | Permitido |
| `POST /api/auth/register` | Permitido | No aplica | No aplica |
| `POST /api/auth/refresh` | Permitido | Permitido | Permitido |
| `POST /api/auth/logout` | Denegado (401) | Permitido | Permitido |
| `POST /api/auth/password/solicitar-reset` | Permitido | Permitido | Permitido |
| `POST /api/auth/password/confirmar-reset` | Permitido | Permitido | Permitido |
| `GET /api/usuarios/me` | Denegado (401) | Permitido | Permitido |
| `PATCH /api/usuarios/me` | Denegado (401) | Permitido | Permitido |
| `GET /api/admin/usuarios` | Denegado (401) | Denegado (403) | Permitido |
| `POST /api/admin/usuarios` | Denegado (401) | Denegado (403) | Permitido |
| `GET /api/admin/usuarios/{id}` | Denegado (401) | Denegado (403) | Permitido |
| `PATCH /api/admin/usuarios/{id}` | Denegado (401) | Denegado (403) | Permitido |
| `PATCH /api/admin/usuarios/{id}/aprobar` | Denegado (401) | Denegado (403) | Permitido |
| `DELETE /api/admin/usuarios/{id}` | Denegado (401) | Denegado (403) | Permitido (no sobre sí mismo) |
| `GET /api/reservas/disponibles` | Denegado (401) | Permitido | Permitido |
| `GET /api/reservas` | Denegado (401) | Permitido (propias) | Permitido (todas) |
| `POST /api/reservas` | Denegado (401) | Permitido | Permitido |
| `GET /api/reservas/{id}` | Denegado (401) | Permitido (si owner o participante) | Permitido |
| `DELETE /api/reservas/{id}` | Denegado (401) | Permitido (solo si owner) | Permitido |
| `POST /api/reservas/{id}/unirse` | Denegado (401) | Permitido | Permitido |
| `GET /api/admin/reservas` | Denegado (401) | Denegado (403) | Permitido |
| `PATCH /api/admin/reservas/{id}/estado` | Denegado (401) | Denegado (403) | Permitido |
| `GET /api/pagos` | Denegado (401) | Permitido (propios) | Permitido |
| `POST /api/pagos/iniciar` | Denegado (401) | Permitido (si owner) | Permitido |
| `POST /api/admin/pagos/{reservaId}/efectivo` | Denegado (401) | Denegado (403) | Permitido |
| `GET /api/admin/pagos` | Denegado (401) | Denegado (403) | Permitido |
| `POST /api/otp/verificar` | Denegado (401) | Permitido | Permitido |
| `GET /api/admin/sistema/config` | Denegado (401) | Denegado (403) | Permitido |
| `PATCH /api/admin/sistema/config` | Denegado (401) | Denegado (403) | Permitido |
| `POST /api/bot/telegram` (webhook) | Solo con secret token | No aplica | No aplica |
| `POST /api/pagos/webhook` (webhook Redsys) | Solo con HMAC | No aplica | No aplica |

> **Leyenda:** Los webhooks de Telegram y Redsys no usan JWT. Se validan por header específico (ver capabilities `auth-otp-telegram` y `pagos-redsys`).

## Requirements

### Requirement 1: Protección de endpoints admin

**El sistema DEBE denegar el acceso a cualquier endpoint bajo `/api/admin/**` para usuarios con rol USER o sin autenticar, devolviendo el código de estado apropiado.**

#### Scenario: USER intenta acceder a endpoint admin

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `GET /api/admin/usuarios` con su token JWT
- **THEN** el sistema responde con código `403`
- **AND** el cuerpo sigue el esquema `ErrorResponse`
- **AND** la acción se registra en `audit_log` como intento de acceso no autorizado

#### Scenario: Request no autenticado a endpoint admin

- **GIVEN** no se incluye ningún token JWT en la cabecera `Authorization`
- **WHEN** se envía `GET /api/admin/pagos`
- **THEN** el sistema responde con código `401`
- **AND** el cuerpo indica que se requiere autenticación

#### Scenario: ADMIN accede correctamente a endpoint admin

- **GIVEN** un usuario autenticado con `role=ADMIN` y `status=ACTIVE`
- **WHEN** se envía `GET /api/admin/usuarios` con su token JWT
- **THEN** el sistema responde con código `200`
- **AND** devuelve la lista paginada de usuarios

---

### Requirement 2: Asignación de rol por ADMIN

**El sistema DEBE permitir que un ADMIN cambie el rol de cualquier usuario entre ADMIN y USER mediante `PATCH /api/admin/usuarios/{id}`.**

#### Scenario: ADMIN asigna rol ADMIN a un USER

- **GIVEN** un administrador autenticado y un usuario con `role=USER` e `id=55`
- **WHEN** se envía `PATCH /api/admin/usuarios/55` con body `{ "role": "ADMIN" }`
- **THEN** el sistema responde con código `200`
- **AND** el usuario con `id=55` tiene `role=ADMIN` en base de datos
- **AND** la acción `USER_ROLE_CHANGED` se registra en `audit_log` con el actor y el valor anterior/nuevo

#### Scenario: USER no puede modificar su propio rol

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `PATCH /api/usuarios/me` con body `{ "role": "ADMIN" }`
- **THEN** el sistema responde con código `200`
- **AND** el campo `role` no cambia en base de datos (campo ignorado en el DTO de actualización de perfil propio)

---

### Requirement 3: Autorización fina basada en propiedad de recurso

**El sistema DEBE verificar la propiedad del recurso en la capa Application antes de devolver datos o ejecutar operaciones, independientemente del rol. Esta verificación usa el `userId` del JWT, nunca un parámetro de la petición.**

#### Scenario: USER intenta ver la reserva de otro usuario (RN-AUTH-01)

- **GIVEN** la reserva con `id=abc` pertenece al usuario con `id=10`
- **AND** el usuario autenticado tiene `id=20` y no es participante de esa reserva
- **WHEN** se envía `GET /api/reservas/abc` con el token del usuario `id=20`
- **THEN** el sistema responde con código `403`
- **AND** la respuesta no revela si la reserva existe (no `404`)

#### Scenario: USER intenta cancelar la reserva de otro usuario (RN-AUTH-02)

- **GIVEN** la reserva con `id=abc` tiene `owner_id=10`
- **AND** el usuario autenticado tiene `id=20`
- **WHEN** se envía `DELETE /api/reservas/abc` con el token del usuario `id=20`
- **THEN** el sistema responde con código `403`
- **AND** la reserva no se cancela

#### Scenario: USER intenta iniciar pago de reserva ajena (RN-AUTH-04)

- **GIVEN** la reserva con `id=abc` tiene `owner_id=10`
- **AND** el usuario autenticado tiene `id=20`
- **WHEN** se envía `POST /api/pagos/iniciar` con `reservaId=abc` y el token del usuario `id=20`
- **THEN** el sistema responde con código `403`
- **AND** no se inicia ningún proceso de pago

---

## Casos límite

- Los tokens JWT contienen el `userId` y el `role` en el payload. La capa de seguridad valida la firma y la vigencia del token antes de extraer estos valores.
- La verificación de propiedad de recurso ocurre siempre en la capa Application (servicio), nunca en el Controller. Esto garantiza que la lógica de acceso no se bypasea por ningún adaptador (web, bot).
- Un usuario con `status=PENDING` o `status=INACTIVE` que tenga un token válido (generado antes de cambiar su estado) recibe `403` en todos los endpoints protegidos.
- Los endpoints de webhook (`/api/bot/telegram`, `/api/pagos/webhook`) no usan JWT. Su validación es por header específico y están configurados como `permitAll()` en Spring Security, delegando la validación al adaptador correspondiente.

## Dependencias con otras capabilities

- **Todas las capabilities**: esta capability es transversal. Todas las demás dependen de la capa RBAC para proteger sus endpoints.
- **`auth-local`**: proporciona el JWT que contiene los claims `userId` y `role` usados por la verificación de acceso.
- **`usuarios`**: la asignación de rol se implementa en la capability `usuarios` (endpoint `PATCH /api/admin/usuarios/{id}`).
- **`auditoria`**: los accesos denegados (403) se registran en `audit_log`.

## Mockups asociados

Esta capability es **transversal o puramente backend**. No tiene pantallas de usuario directas en el sistema actual.

Está implícita en los siguientes mockups donde el concepto aparece de forma indirecta:

- **`roles-permisos`**: implícita en todas las pantallas protegidas (roles ADMIN/USER controlan la navegación). El pill de rol del usuario aparece en `13 · Mi perfil` → [`14-mi-perfil.html`](../../../docs/ux/mockups/14-mi-perfil.html). La redirección por rol no autorizado (403) se manifiesta en todas las pantallas de admin cuando un USER intenta acceder. Las pantallas admin (`21 · Dashboard club`, `22 · Calendario semanal`, `23 · Reservas del club`, `24 · Gestión de pistas`) solo son accesibles para usuarios con rol ADMIN.

Si se evoluciona esta capability hacia una UI dedicada (ej. panel de gestión de roles en Fase 2), añadir el flujo correspondiente en [`docs/ux/flujos.md`](../../../docs/ux/flujos.md) antes de generar mockups.
