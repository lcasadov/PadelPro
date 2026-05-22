# Capability: usuarios

## Resumen

Gestiona el perfil del usuario autenticado (lectura y actualización de datos propios), la administración de cuentas por parte del ADMIN (creación directa, aprobación de registros pendientes, actualización de datos y desactivación), y el derecho de acceso a datos propios según el RGPD. El borrado de usuarios es siempre una anonimización lógica, nunca un hard delete.

## Fase

Fase 1

## Reglas de negocio implicadas

- **RN-AUTH-05**: Un ADMIN no puede desactivarse a sí mismo.
- **RN-AUTH-08**: Las contraseñas creadas o actualizadas cumplen la política: mínimo 8 chars + 1 mayúscula + 1 número; máximo 128 chars; BCrypt cost 12.
- **RN-RGPD-01**: La eliminación de usuario es anonimización (no hard delete); genera entrada en `audit_log` con acción `USER_ANONYMIZED`.
- **RN-RGPD-02**: Los datos de cuenta se retienen durante la vida de la cuenta más 5 años tras la inactivación.
- **RN-RGPD-03**: Las respuestas de error no exponen datos personales de otros usuarios.

## Entidades implicadas

**users** (tabla `users`):
- `id`, `login`, `password_hash`, `first_name`, `last_name`, `phone`, `email`, `status` (`PENDING|ACTIVE|INACTIVE`), `role` (`ADMIN|USER`), `telegram_chat_id`, `telegram_linked_at`, `registered_at`, `updated_at`

## Endpoints

| Método | Path | OperationId | Autenticación |
|---|---|---|---|
| `GET` | `/api/usuarios/me` | `getMiPerfil` | JWT requerido |
| `PATCH` | `/api/usuarios/me` | `actualizarMiPerfil` | JWT requerido |
| `GET` | `/api/admin/usuarios` | `listarUsuarios` | JWT requerido (ADMIN) |
| `POST` | `/api/admin/usuarios` | `crearUsuario` | JWT requerido (ADMIN) |
| `GET` | `/api/admin/usuarios/{id}` | `getUsuario` | JWT requerido (ADMIN) |
| `PATCH` | `/api/admin/usuarios/{id}` | `actualizarUsuario` | JWT requerido (ADMIN) |
| `PATCH` | `/api/admin/usuarios/{id}/aprobar` | `aprobarUsuario` | JWT requerido (ADMIN) |
| `DELETE` | `/api/admin/usuarios/{id}` | `desactivarUsuario` | JWT requerido (ADMIN) |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| `GET /api/usuarios/me` | Permitido (propio) | Permitido (propio) | Denegado (401) |
| `PATCH /api/usuarios/me` | Permitido (propio) | Permitido (propio) | Denegado (401) |
| `GET /api/admin/usuarios` | Permitido | Denegado (403) | Denegado (401) |
| `POST /api/admin/usuarios` | Permitido | Denegado (403) | Denegado (401) |
| `GET /api/admin/usuarios/{id}` | Permitido | Denegado (403) | Denegado (401) |
| `PATCH /api/admin/usuarios/{id}` | Permitido | Denegado (403) | Denegado (401) |
| `PATCH /api/admin/usuarios/{id}/aprobar` | Permitido | Denegado (403) | Denegado (401) |
| `DELETE /api/admin/usuarios/{id}` | Permitido (no sobre sí mismo) | Denegado (403) | Denegado (401) |

## Requirements

### Requirement 1: Consulta y actualización del perfil propio

**El sistema DEBE permitir que cualquier usuario autenticado consulte y actualice sus propios datos de perfil. Los campos `role` y `status` son de solo lectura para el propio usuario.**

#### Scenario: USER consulta su propio perfil

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `GET /api/usuarios/me` con su token JWT
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene `id`, `login`, `firstName`, `lastName`, `email`, `phone`, `status`, `role`, y el estado de vinculación de Telegram
- **AND** la respuesta no incluye `password_hash` ni datos de otros usuarios

#### Scenario: USER actualiza campos permitidos de su perfil

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `PATCH /api/usuarios/me` con `firstName`, `lastName`, `email` o `phone` nuevos y válidos
- **THEN** el sistema responde con código `200`
- **AND** los campos actualizados se persisten en base de datos
- **AND** `updated_at` se actualiza al momento actual

#### Scenario: USER no puede modificar su propio rol ni estado

- **GIVEN** un usuario autenticado con `role=USER`
- **WHEN** se envía `PATCH /api/usuarios/me` con body que incluye `role: "ADMIN"` o `status: "ACTIVE"`
- **THEN** el sistema responde con código `200` pero los campos `role` y `status` no cambian en base de datos
- **AND** el sistema ignora silenciosamente los campos no permitidos (deserialización restrictiva)

#### Scenario: Conflicto al actualizar email ya registrado

- **GIVEN** otro usuario tiene el email `otro@example.com`
- **WHEN** el usuario autenticado envía `PATCH /api/usuarios/me` con `email=otro@example.com`
- **THEN** el sistema responde con código `409`
- **AND** el mensaje indica conflicto de email sin revelar datos del otro usuario (RN-RGPD-03)

---

### Requirement 2: Administración de usuarios por ADMIN

**El sistema DEBE permitir que el ADMIN cree usuarios directamente (sin proceso de aprobación), apruebe registros pendientes y actualice cualquier campo incluidos `role` y `status`.**

#### Scenario: ADMIN crea usuario directamente con estado ACTIVE

- **GIVEN** un administrador autenticado con `role=ADMIN`
- **WHEN** se envía `POST /api/admin/usuarios` con datos válidos de un nuevo usuario
- **THEN** el sistema responde con código `201`
- **AND** el usuario se crea con `status=ACTIVE` (sin paso de aprobación)
- **AND** la contraseña se almacena como BCrypt cost 12 (RN-AUTH-08)

#### Scenario: ADMIN aprueba cuenta en estado PENDING

- **GIVEN** existe un usuario con `status=PENDING` (auto-registrado, no aprobado)
- **WHEN** se envía `PATCH /api/admin/usuarios/{id}/aprobar`
- **THEN** el sistema responde con código `200`
- **AND** el usuario pasa a `status=ACTIVE`
- **AND** se registra la acción `USER_APPROVED` en `audit_log`

#### Scenario: ADMIN intenta aprobar cuenta que no está en PENDING

- **GIVEN** existe un usuario con `status=ACTIVE` o `status=INACTIVE`
- **WHEN** se envía `PATCH /api/admin/usuarios/{id}/aprobar`
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que el usuario no está en estado PENDING

#### Scenario: ADMIN desactiva un usuario (soft-delete / borrado lógico — RN-RGPD-01)

- **GIVEN** existe un usuario con `status=ACTIVE` cuyo `id` es diferente al del ADMIN autenticado
- **WHEN** se envía `DELETE /api/admin/usuarios/{id}`
- **THEN** el sistema responde con código `204`
- **AND** el usuario pasa a `status=INACTIVE`
- **AND** el registro permanece en base de datos con sus datos intactos
- **AND** se registra la acción `USER_DEACTIVATED` en `audit_log`

---

### Requirement 3: Protección de la identidad del ADMIN (RN-AUTH-05)

**El sistema DEBE impedir que un ADMIN se desactive a sí mismo.**

#### Scenario: ADMIN intenta desactivarse a sí mismo

- **GIVEN** un administrador autenticado cuyo `id` es `42`
- **WHEN** se envía `DELETE /api/admin/usuarios/42` con el token JWT del propio admin
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que un administrador no puede desactivarse a sí mismo
- **AND** el estado del admin no cambia en base de datos

---

## Casos límite

- El `login` de un usuario nunca cambia una vez creado; no hay endpoint para modificarlo.
- La lista de usuarios (`GET /api/admin/usuarios`) está paginada (máximo 100 por página) y soporta filtrado por `status`.
- Si se intenta crear un usuario con `login`, `email` o `phone` duplicado, el sistema responde `409`.
- Un usuario en estado `INACTIVE` no puede autenticarse. Si intenta hacer login, recibe `403`.
- El campo `telegram_chat_id` solo puede modificarse a través del flujo de vinculación/desvinculación (capability `auth-otp-telegram`), no directamente en `PATCH /api/usuarios/me` ni en `PATCH /api/admin/usuarios/{id}`.

## Dependencias con otras capabilities

- **`auth-local`**: el registro crea el usuario en estado `PENDING`; la aprobación es responsabilidad de esta capability.
- **`auth-otp-telegram`**: la vinculación Telegram modifica `telegram_chat_id` en la entidad `users`.
- **`roles-permisos`**: los endpoints `/api/admin/usuarios/**` están protegidos por la capa RBAC que verifica `role=ADMIN`.
- **`auditoria`**: cada operación administrativa (creación, aprobación, desactivación, cambio de rol) genera una entrada en `audit_log`.
- **`exportaciones-rgpd`**: la anonimización completa de datos personales (derecho de supresión RGPD) se implementa como extensión del endpoint `DELETE /api/admin/usuarios/{id}`.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 03 | Crear cuenta | Mobile | público | [`08-crear-cuenta.html`](../../../docs/ux/mockups/08-crear-cuenta.html) |
| 13 | Mi perfil | Mobile | USER | [`14-mi-perfil.html`](../../../docs/ux/mockups/14-mi-perfil.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de onboarding y autenticación** — el formulario de creación de cuenta (pantalla 03) recoge los datos del nuevo usuario que esta capability persiste en estado PENDING.
- **Flujo de vinculación Telegram** — la pantalla de Mi perfil (pantalla 13) es el punto de entrada al flujo de vinculación, y también donde el usuario actualiza sus datos de perfil.

### Notas de UX

> - El mensaje de error al actualizar email duplicado no debe revelar datos del otro usuario (RN-RGPD-03); la pantalla debe indicar solo que el email ya está en uso.
> - Los campos `role` y `status` no son editables por el propio usuario desde Mi perfil; el formulario no debe mostrar controles para modificarlos.
