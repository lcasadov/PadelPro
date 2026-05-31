## MODIFIED Requirements

### Requirement: Consulta y actualización del perfil propio

**El sistema DEBE permitir que cualquier usuario autenticado consulte y actualice sus propios datos de perfil. Los campos `role` y `status` son de solo lectura para el propio usuario.**

#### Scenario: USER consulta su propio perfil

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/usuarios/me` con su token JWT válido
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene `id`, `login`, `firstName`, `lastName`, `email`, `phone`, `status`, `role`, y el estado de vinculación de Telegram
- **AND** la respuesta no incluye `password_hash` ni datos de otros usuarios

#### Scenario: ADMIN consulta su propio perfil

- **WHEN** un usuario autenticado con `role=ADMIN` envía `GET /api/usuarios/me` con su token JWT válido
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene `role=ADMIN` para confirmar que el endpoint sirve correctamente a ambos roles

#### Scenario: Usuario no autenticado intenta acceder al perfil

- **WHEN** se envía `GET /api/usuarios/me` sin cabecera `Authorization`
- **THEN** el sistema responde con código `401`
- **AND** el body contiene un `ErrorResponse` con código `AUTH_TOKEN_MISSING` o equivalente

#### Scenario: USER actualiza campos permitidos de su perfil

- **WHEN** un usuario autenticado con `role=USER` envía `PATCH /api/usuarios/me` con `firstName`, `lastName`, `email` o `phone` nuevos y válidos
- **THEN** el sistema responde con código `200`
- **AND** los campos actualizados se persisten en base de datos
- **AND** `updated_at` se actualiza al momento actual

#### Scenario: USER no puede modificar su propio rol ni estado

- **WHEN** un usuario autenticado con `role=USER` envía `PATCH /api/usuarios/me` con body que incluye `role: "ADMIN"` o `status: "ACTIVE"`
- **THEN** el sistema responde con código `200` pero los campos `role` y `status` no cambian en base de datos
- **AND** el sistema ignora silenciosamente los campos no permitidos (deserialización restrictiva)

#### Scenario: Conflicto al actualizar email ya registrado

- **WHEN** el usuario autenticado envía `PATCH /api/usuarios/me` con un `email` que ya pertenece a otro usuario
- **THEN** el sistema responde con código `409`
- **AND** el mensaje indica conflicto de email (`USUARIOS_EMAIL_CONFLICT`) sin revelar datos del otro usuario (RN-RGPD-03)

#### Scenario: Email con formato inválido en actualización de perfil

- **WHEN** el usuario autenticado envía `PATCH /api/usuarios/me` con un `email` que no tiene formato de email válido (p.ej. `"no-es-un-email"`)
- **THEN** el sistema responde con código `400`
- **AND** el mensaje indica validación fallida en el campo `email`

---

### Requirement: Administración de usuarios por ADMIN

**El sistema DEBE permitir que el ADMIN cree usuarios directamente (sin proceso de aprobación), apruebe registros pendientes, liste usuarios con paginación y filtro por estado, acceda al detalle de cualquier usuario y actualice cualquier campo incluidos `role` y `status`.**

#### Scenario: ADMIN crea usuario directamente con estado ACTIVE

- **WHEN** un administrador autenticado con `role=ADMIN` envía `POST /api/admin/usuarios` con datos válidos (login, firstName, lastName, email, password) de un nuevo usuario
- **THEN** el sistema responde con código `201`
- **AND** el usuario se crea con `status=ACTIVE` (sin paso de aprobación)
- **AND** la contraseña se almacena como BCrypt cost 12 (RN-AUTH-08)
- **AND** se registra la acción `USER_CREATED_BY_ADMIN` en `audit_log`

#### Scenario: ADMIN intenta crear usuario con email duplicado

- **WHEN** un administrador autenticado envía `POST /api/admin/usuarios` con un email que ya existe en la base de datos
- **THEN** el sistema responde con código `409`
- **AND** el mensaje indica conflicto de email sin revelar datos del usuario existente (RN-RGPD-03)

#### Scenario: ADMIN aprueba cuenta en estado PENDING

- **WHEN** existe un usuario con `status=PENDING` y el ADMIN envía `PATCH /api/admin/usuarios/{id}/aprobar`
- **THEN** el sistema responde con código `200`
- **AND** el usuario pasa a `status=ACTIVE`
- **AND** se registra la acción `USER_APPROVED` en `audit_log`

#### Scenario: ADMIN intenta aprobar cuenta que no está en PENDING

- **WHEN** existe un usuario con `status=ACTIVE` o `status=INACTIVE` y el ADMIN envía `PATCH /api/admin/usuarios/{id}/aprobar`
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que el usuario no está en estado PENDING

#### Scenario: ADMIN lista usuarios sin filtro (paginado)

- **WHEN** un ADMIN envía `GET /api/admin/usuarios` sin parámetros adicionales
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene un objeto paginado con `content`, `totalElements`, `totalPages`, `page`, `size`
- **AND** `size` es como máximo 100 aunque el cliente haya pedido más

#### Scenario: ADMIN lista usuarios filtrando por estado

- **WHEN** un ADMIN envía `GET /api/admin/usuarios?status=PENDING`
- **THEN** el sistema responde con código `200`
- **AND** todos los usuarios en `content` tienen `status=PENDING`

#### Scenario: ADMIN obtiene detalle de usuario existente

- **WHEN** un ADMIN envía `GET /api/admin/usuarios/{id}` con un `id` válido
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene todos los campos del perfil del usuario

#### Scenario: ADMIN intenta obtener usuario inexistente

- **WHEN** un ADMIN envía `GET /api/admin/usuarios/{id}` con un `id` que no existe
- **THEN** el sistema responde con código `404`
- **AND** el mensaje indica que el usuario no fue encontrado

#### Scenario: ADMIN desactiva un usuario (soft-delete / borrado lógico — RN-RGPD-01)

- **WHEN** existe un usuario con `status=ACTIVE` cuyo `id` es diferente al del ADMIN autenticado, y el ADMIN envía `DELETE /api/admin/usuarios/{id}`
- **THEN** el sistema responde con código `204`
- **AND** el usuario pasa a `status=INACTIVE`
- **AND** el registro permanece en base de datos con sus datos intactos
- **AND** se registra la acción `USER_DEACTIVATED` en `audit_log`

#### Scenario: Usuario no ADMIN intenta acceder a endpoint de administración

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/admin/usuarios`
- **THEN** el sistema responde con código `403`

---

### Requirement: Protección de la identidad del ADMIN (RN-AUTH-05)

**El sistema DEBE impedir que un ADMIN se desactive a sí mismo.**

#### Scenario: ADMIN intenta desactivarse a sí mismo

- **WHEN** un administrador autenticado cuyo `id` es `42` envía `DELETE /api/admin/usuarios/42` con su propio token JWT
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que un administrador no puede desactivarse a sí mismo
- **AND** el estado del admin no cambia en base de datos

#### Scenario: ADMIN desactiva a otro usuario sin afectar su propia cuenta

- **WHEN** el ADMIN autenticado con `id=42` envía `DELETE /api/admin/usuarios/99` (id diferente)
- **THEN** el sistema responde con código `204`
- **AND** la cuenta con `id=99` pasa a `status=INACTIVE`
- **AND** la cuenta del ADMIN con `id=42` no se ve afectada
