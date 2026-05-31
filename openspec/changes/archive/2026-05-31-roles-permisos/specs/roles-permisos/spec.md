## MODIFIED Requirements

### Requirement: Protección de endpoints admin

**El sistema DEBE denegar el acceso a cualquier endpoint bajo `/api/admin/**` para usuarios con rol USER o sin autenticar, devolviendo el código de estado apropiado con cuerpo `ErrorResponse` JSON.**

#### Scenario: USER intenta acceder a endpoint admin

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/admin/usuarios` con su token JWT
- **THEN** el sistema responde con código `403`
- **AND** el cuerpo es `{ "error": "ACCESS_DENIED", "message": "Insufficient permissions" }`
- **AND** la acción `ACCESS_DENIED` se registra en `audit_log` con el `user_id` del token y la URI solicitada

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

### Requirement: Asignación de rol por ADMIN

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

### Requirement: Autorización fina basada en propiedad de recurso

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
