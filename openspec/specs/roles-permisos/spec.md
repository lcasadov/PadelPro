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

**El sistema DEBE denegar el acceso a cualquier endpoint bajo `/api/admin/**` para usuarios con rol USER o sin autenticar, devolviendo el código de estado apropiado con cuerpo `ErrorResponse` JSON.**

#### Scenario: USER intenta acceder a endpoint admin

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/admin/usuarios` con su token JWT
- **THEN** el sistema responde con código `403`
- **AND** el cuerpo es `{ "error": "ACCESS_DENIED", "message": "Insufficient permissions" }`
- **AND** la acción `ACCESS_DENIED` se registra en `audit_log` con el `user_id` del token (FK) y la URI solicitada

#### Scenario: Request no autenticado a endpoint admin

- **WHEN** se envía `GET /api/admin/pagos` sin cabecera `Authorization`
- **THEN** el sistema responde con código `401`
- **AND** el cuerpo es `{ "error": "AUTH_REQUIRED", "message": "Authentication required" }`

#### Scenario: ADMIN accede correctamente a endpoint admin

- **WHEN** un usuario autenticado con `role=ADMIN` y `status=ACTIVE` envía `GET /api/admin/usuarios` con su token JWT
- **THEN** el sistema responde con código `200`
- **AND** devuelve la lista paginada de usuarios

#### Scenario: Usuario desactivado con token válido recibe 403

- **WHEN** un usuario cuyo `status` fue cambiado a `INACTIVE` (o `PENDING`) después de emitir su JWT envía cualquier request con ese token
- **THEN** el sistema responde con código `403`
- **AND** el cuerpo contiene `{ "error": "ACCOUNT_NOT_ACTIVE", "message": "Account is not active" }`
- **AND** el SecurityContext se limpia para esa request

#### Scenario: Request a webhook no requiere JWT

- **WHEN** se envía `POST /api/bot/telegram` o `POST /api/pagos/webhook` sin cabecera `Authorization`
- **THEN** el sistema no responde `401` por ausencia de JWT (la autenticación es responsabilidad del adaptador)
- **AND** la validación específica del adaptador (secret token, HMAC) sí puede rechazar con `401` o `403`

---

### Requirement 2: Asignación de rol por ADMIN

**El sistema DEBE permitir que un ADMIN cambie el rol de cualquier usuario entre ADMIN y USER, y prohibir que cualquier usuario modifique su propio rol.**

#### Scenario: ADMIN asigna rol ADMIN a un USER

- **WHEN** un administrador autenticado envía `PATCH /api/admin/usuarios/55` con body `{ "role": "ADMIN" }`
- **THEN** el sistema responde con código `200`
- **AND** el usuario con `id=55` tiene `role=ADMIN` en base de datos
- **AND** la acción `USER_ROLE_CHANGED` se registra en `audit_log` con el actor, el valor anterior y el nuevo valor

#### Scenario: ADMIN revoca rol ADMIN a otro ADMIN

- **WHEN** un administrador autenticado envía `PATCH /api/admin/usuarios/55` con body `{ "role": "USER" }` siendo `55` otro ADMIN (no él mismo)
- **THEN** el sistema responde con código `200`
- **AND** el usuario `id=55` tiene `role=USER` en base de datos
- **AND** se registra `USER_ROLE_CHANGED` en `audit_log`

#### Scenario: USER no puede modificar su propio rol

- **WHEN** un usuario autenticado con `role=USER` envía `PATCH /api/usuarios/me` con body `{ "role": "ADMIN" }`
- **THEN** el sistema responde con código `200`
- **AND** el campo `role` no cambia en base de datos (campo ausente en `UpdateMyProfileCommand`)

---

### Requirement 3: Autorización fina basada en propiedad de recurso

**El sistema DEBE verificar la propiedad del recurso en la capa Application antes de devolver datos o ejecutar operaciones. Las verificaciones ocurren en el servicio, nunca en el controlador. El `userId` se extrae siempre del JWT, nunca de parámetros de la petición.**

#### Scenario: USER intenta ver la reserva de otro usuario — 403 sin revelar existencia (RN-AUTH-01)

- **WHEN** el usuario autenticado con `id=20` envía `GET /api/reservas/abc` siendo `id=10` el `owner_id` de la reserva y `id=20` no siendo participante
- **THEN** el sistema responde con código `403`
- **AND** la respuesta NO revela si la reserva existe (prevención BOLA — `403`, nunca `404`)

#### Scenario: USER intenta cancelar la reserva de otro usuario (RN-AUTH-02)

- **WHEN** el usuario autenticado con `id=20` envía `DELETE /api/reservas/abc` siendo `owner_id=10`
- **THEN** el sistema responde con código `403`
- **AND** la reserva no cambia de estado

#### Scenario: USER intenta iniciar pago de reserva ajena (RN-AUTH-04)

- **WHEN** el usuario autenticado con `id=20` envía `POST /api/pagos/iniciar` con `reservaId=abc` siendo `owner_id=10`
- **THEN** el sistema responde con código `403`
- **AND** no se inicia ningún proceso de pago

#### Scenario: ArchUnit — verificación de propiedad nunca en Controller

- **WHEN** se compila el proyecto con ArchUnit activo
- **THEN** ninguna clase en `infrastructure.web` llama directamente a `ResourceOwnershipPort`
- **AND** el build falla si algún controlador viola esta regla

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
