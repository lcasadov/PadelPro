## Context

`auth-local` creó la entidad `User`, `UserRepositoryPort` y la tabla `users` en estado `PENDING`. Sin embargo, no existe ningún mecanismo para que un ADMIN active esas cuentas, ni para que cualquier usuario consulte o actualice sus propios datos. Este change cierra ese hueco añadiendo el módulo `usuarios` (hexagonal) que expone ocho endpoints REST sobre la entidad ya existente.

**Stake técnicos heredados:**
- Entidad `User` (JPA) y `UserRepositoryPort` (puerto de dominio) definidos en `com.padelpro.auth.domain`.
- Tabla `users` con columnas: `id`, `login`, `password_hash`, `first_name`, `last_name`, `phone`, `email`, `status`, `role`, `telegram_chat_id`, `telegram_linked_at`, `registered_at`, `updated_at`.
- `AuditLogRepository` disponible en `com.padelpro.auth.infrastructure.persistence`.
- Spring Security config JWT stateless activa; roles `ADMIN` / `USER` en el JWT claims.

---

## Goals / Non-Goals

**Goals:**
- Exponer perfil propio (`GET/PATCH /api/usuarios/me`) para cualquier usuario autenticado.
- Exponer CRUD de usuarios (`/api/admin/usuarios/**`) exclusivo para ADMIN.
- Implementar RN-AUTH-05 (ADMIN no puede desactivarse a sí mismo).
- Implementar borrado lógico vía `status=INACTIVE` (RN-RGPD-01).
- Auditar todas las acciones administrativas en `audit_log`.

**Non-Goals:**
- Anonimización RGPD completa de datos personales → `exportaciones-rgpd`.
- Vinculación / desvinculación del canal Telegram → `auth-otp-telegram`.
- Reset o cambio de contraseña → `auth-password-reset`.
- UI admin de tabla de usuarios → `administracion-club` (Fase 2).
- Gestión de login (campo inmutable; nunca cambia tras la creación).

---

## Decisions

### D-1 — Reutilizar entidad y repositorio de `auth-local`

El módulo `usuarios` **no declara su propia entidad JPA**. Importa directamente `User` y `UserRepositoryPort` del módulo `com.padelpro.auth.domain`. Esto evita duplicación de la fuente de verdad y mantiene una única tabla `users`.

*Alternativa descartada*: crear una entidad `UsuarioProjection` independiente. Descartada porque requeriría sincronización bidireccional y duplicaría el esquema.

### D-2 — Dos servicios de aplicación diferenciados (RN-RGPD-03)

Se crean dos servicios separados:
- `UserProfileService`: operaciones del propio usuario autenticado. Expone solo los campos que el usuario puede leer/escribir sobre sí mismo.
- `UserAdminService`: operaciones de ADMIN. Gestiona el ciclo de vida completo de cuentas.

*Razón*: evitar lógica condicional `if (caller.isAdmin()) { … }` dentro de un único servicio. DTOs distintos son la barrera más robusta contra escalada de privilegios accidental.

### D-3 — DTOs separados para auto-actualización vs actualización por ADMIN

`UpdateMyProfileCommand` acepta solo `firstName`, `lastName`, `email`, `phone`.  
`UpdateUserAdminCommand` acepta adicionalmente `role`, `status`.

El campo `login` nunca aparece en ningún comando de actualización (inmutable post-creación, RN implícita).

*Alternativa descartada*: un único DTO con campos opcionales + validación en servicio. Descartada porque la seguridad no debe depender de lógica de servicio para el filtrado de campos sensibles.

### D-4 — Protección RN-AUTH-05 en capa de servicio

La verificación `targetId == authenticatedAdminId` se implementa en `UserAdminService.deactivate()` antes de cualquier persistencia. Lanza `AdminSelfDeactivationException` (→ HTTP 422).

*Alternativa descartada*: constraint en BD. Descartada porque la regla es de negocio (no de integridad referencial) y un constraint de BD no podría generar un mensaje de error semántico.

### D-5 — Aprobación solo sobre estado PENDING

`PATCH /api/admin/usuarios/{id}/aprobar` verifica que `user.status == PENDING`. Si no, lanza `UserNotPendingException` (→ HTTP 422).

*Razón*: aprobar una cuenta INACTIVE podría ocultar que fue desactivada por una razón. La transición solo PENDING → ACTIVE es intencionada.

### D-6 — Paginación con límite forzado de 100 en servicio

`GET /api/admin/usuarios` acepta parámetros `page` y `size` (Spring Data `Pageable`). El servicio fuerza `size = min(size, 100)` antes de pasar a repositorio. Filtro por `status` optional mediante query derivada de Spring Data.

*Razón*: evitar que un cliente malintencionado pida `size=10000` generando un resultado masivo. Máximo 100 es coherente con los "Casos límite" de la spec canónica.

### D-7 — Soft delete: `status=INACTIVE`, sin hard delete (RN-RGPD-01)

`DELETE /api/admin/usuarios/{id}` cambia `status=INACTIVE`. El registro permanece intacto en BD. Registro en `audit_log` con acción `USER_DEACTIVATED`. La anonimización completa (nombres, email) queda diferida a `exportaciones-rgpd`.

### D-8 — Reutilizar `AuditLogRepository` de `auth-local`

Se añaden cuatro nuevas acciones al enum `AuditAction` (o constantes de string, según implementación de auth-local):
- `USER_CREATED_BY_ADMIN`
- `USER_APPROVED`
- `USER_DEACTIVATED`
- `USER_ROLE_CHANGED`

*Razón*: centralizar la auditoría en una sola tabla `audit_log` sin romper la consistencia de auth-local.

### D-9 — Extensión de Spring Security: regla `ADMIN` para `/api/admin/**`

Se añade a la config de `SecurityFilterChain` existente:
```
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/usuarios/me").authenticated()
```
No se crea un nuevo `SecurityFilterChain`; se extiende el de `auth-local`.

### D-10 — Migración Flyway: sin nueva tabla

La tabla `users` ya existe (V1). No se requiere migración adicional para este change. Si se añaden columnas o índices en el futuro, se numerarán V4 o superior (V3 ya existe en audit_log).

### D-11 — Respuesta de conflicto de email (RN-RGPD-03)

Cuando `PATCH /api/usuarios/me` recibe un `email` ya registrado por otro usuario, el sistema responde `409 CONFLICT` con `ErrorResponse` genérico:
```json
{ "code": "USUARIOS_EMAIL_CONFLICT", "message": "El email ya está registrado" }
```
Sin revelar a quién pertenece el email conflictivo.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| Dependencia circular: `usuarios` importa de `auth` | Extraer `User` y `UserRepositoryPort` a un módulo compartido `padelpro-domain` si la dependencia se vuelve problemática en futuras capabilities. Por ahora la dependencia directa es aceptable dado que ambos son Fase 1. |
| Campo `telegram_chat_id` editable accidentalmente vía PATCH admin | Excluir explícitamente `telegramChatId` de `UpdateUserAdminCommand`. Documentar en spec que solo `auth-otp-telegram` puede modificarlo. |
| Listado de usuarios expone datos personales masivos | Endpoint protegido por `ADMIN` + paginación máx 100. Suficiente para MVP. Anonimización por campo no es necesaria en este change. |
| BCrypt en `POST /api/admin/usuarios` | El servicio llama al mismo `PasswordEncoder` (BCrypt cost 12) usado en `RegistrationService`. No se duplica la lógica de hashing. |

---

## Migration Plan

1. Añadir acciones de auditoría al enum/constantes de `auth-local` (sin migración SQL).
2. Crear módulo `com.padelpro.usuarios` con estructura hexagonal vacía (compila).
3. Implementar DTOs y puertos de dominio.
4. Implementar servicios `UserProfileService` y `UserAdminService`.
5. Implementar `UsuariosController` y `AdminUsuariosController`.
6. Extender `SecurityFilterChain` con las nuevas reglas.
7. Tests unitarios (JUnit 5 + Mockito) y de integración (Testcontainers).
8. Rollback: el módulo es aditivo; eliminar el módulo no afecta al módulo `auth-local`.

---

## Open Questions

*(Ninguna — todas las decisiones están cerradas para este change)*
