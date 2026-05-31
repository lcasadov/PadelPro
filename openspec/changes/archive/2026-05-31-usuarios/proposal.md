# Proposal: Gestión de usuarios

**Change slug:** `usuarios`
**Estado:** proposed
**Issue GitHub:** TBD
**Épica:** EP-01 Acceso e Identidad
**Milestone:** EP-01 Acceso e Identidad

---

## Why

`auth-local` creó el modelo `User` y el flujo de auto-registro, pero los usuarios quedan en estado `PENDING` sin que nadie pueda activarlos, ver su perfil ni modificar sus datos. Esta capability cierra ese hueco: da al ADMIN las herramientas para gestionar el ciclo de vida de las cuentas, y a cada usuario la capacidad de consultar y actualizar su propio perfil.

---

## What Changes

- Nuevo endpoint `GET /api/usuarios/me` — el usuario autenticado consulta su propio perfil (US-003 parcial, US-001 completar).
- Nuevo endpoint `PATCH /api/usuarios/me` — actualización de nombre, apellido, email y teléfono; `role` y `status` son inmutables para el propio usuario.
- Nuevo endpoint `POST /api/admin/usuarios` — el ADMIN crea usuarios directamente en estado `ACTIVE` (US-004).
- Nuevo endpoint `PATCH /api/admin/usuarios/{id}/aprobar` — el ADMIN aprueba cuentas en estado `PENDING` (desbloquea usuarios auto-registrados via auth-local).
- Nuevo endpoint `GET /api/admin/usuarios` — listado paginado con filtro por `status`.
- Nuevo endpoint `GET /api/admin/usuarios/{id}` — detalle de un usuario.
- Nuevo endpoint `PATCH /api/admin/usuarios/{id}` — ADMIN actualiza cualquier campo incluidos `role` y `status`.
- Nuevo endpoint `DELETE /api/admin/usuarios/{id}` — desactivación lógica (`status=INACTIVE`); no hard-delete (RN-RGPD-01).
- Regla RN-AUTH-05 implementada: ADMIN no puede desactivarse a sí mismo.
- Auditoría de todas las acciones administrativas en `audit_log`.

---

## Capabilities

### New Capabilities

_(ninguna — `usuarios` ya existe como spec base; este change lo implementa)_

### Modified Capabilities

- `usuarios`: implementación completa de los 3 requirements existentes en `openspec/specs/usuarios/spec.md` (R-1 perfil propio, R-2 administración ADMIN, R-3 protección auto-desactivación). Se añaden scenarios de error y casos límite.

---

## Impact

**Backend:**
- Nuevo módulo `com.padelpro.usuarios` (hexagonal: domain, application, infrastructure).
- Reutiliza entidad `User` y `UserRepository` creados en `auth-local`.
- Nuevos servicios: `UserProfileService` (perfil propio), `UserAdminService` (gestión admin).
- Nuevos endpoints bajo `/api/usuarios/me` y `/api/admin/usuarios/**`.
- Spring Security: roles ADMIN requeridos en endpoints `/api/admin/**` (extiende config de auth-local).

**Frontend:**
- Pantalla `14-mi-perfil.html` (Mi perfil): formulario de lectura/edición de datos propios.
- Pantalla admin de gestión de usuarios: tabla con paginación, filtro por estado, botón de aprobar/desactivar (forma parte del panel admin, Fase 2 en UI; para este change solo se implementa la API).

**Dependencias:**
- Requiere `auth-local` (entidad User, JWT en Authorization header).
- Desbloquea: `auth-otp-telegram` (necesita `telegram_chat_id` editable), `reservas` (necesita usuarios activos como titulares), `exportaciones-rgpd` (extiende DELETE con anonimización).

---

## Alcance fuera del change

| Capacidad excluida | Change futuro |
|---|---|
| Anonimización RGPD completa (borrado de datos personales) | `exportaciones-rgpd` |
| Vinculación / desvinculación de Telegram | `auth-otp-telegram` |
| Gestión de contraseñas (reset, cambio) | `auth-password-reset` |
| Historial de reservas desde el perfil | `reservas` |
| UI admin completa (tabla de usuarios en dashboard) | `administracion-club` (Fase 2) |
