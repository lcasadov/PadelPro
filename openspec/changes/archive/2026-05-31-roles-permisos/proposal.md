# Proposal: Roles y Permisos

**Change slug:** `roles-permisos`
**Estado:** proposed
**Issue GitHub:** TBD
**Épica:** EP-01 Acceso e Identidad
**Milestone:** EP-01 Acceso e Identidad

---

## Why

`auth-local` y `usuarios` implementaron autenticación JWT y RBAC básico (`hasRole('ADMIN')` en `/api/admin/**`), pero la capa de seguridad tiene huecos críticos: un usuario cuyo estado cambia a `INACTIVE` después de recibir un token sigue pudiendo operar hasta que el token expire (15 min), los accesos denegados (403) no se registran en `audit_log`, y los endpoints de webhook futuros (`/api/bot/telegram`, `/api/pagos/webhook`) no tienen su configuración `permitAll()` preparada. Este change cierra esos huecos y formaliza el contrato de seguridad transversal que todas las capabilities restantes asumirán como dado.

---

## What Changes

- **Nuevo `UserStatusFilter`**: verificación del `status` del usuario en cada petición autenticada. Un usuario con `status=PENDING` o `status=INACTIVE` recibe `403` aunque su JWT sea válido — sin excepción, sin importar cuándo fue emitido el token.
- **`AccessDeniedHandler` + `AuthenticationEntryPoint` personalizados**: respuestas JSON consistentes (`ErrorResponse`) para 401 y 403, con registro automático en `audit_log` (acción `ACCESS_DENIED`) para todos los 403.
- **Configuración de rutas de webhook** en `SecurityFilterChain`: `permitAll()` para `/api/bot/telegram` y `/api/pagos/webhook` (la validación real la delegan al adaptador — secret token y HMAC respectivamente).
- **JaCoCo**: umbral mínimo de cobertura del 80% de líneas configurado en Maven. Exclusiones explícitas: DTOs, enums, clases generadas.
- **ArchUnit — nueva regla**: las verificaciones de propiedad de recurso (RN-AUTH-01, RN-AUTH-02, RN-AUTH-03, RN-AUTH-04) ocurren en la capa `application.service`, nunca en `infrastructure.web`. Regla que bloquea el build si se viola.
- **`ResourceOwnershipPort`**: interfaz en `domain.port.in` que define el contrato de verificación de propiedad de recurso para `reservas` y `pagos-redsys`. Las implementaciones vendrán en esos changes; aquí solo se declara la abstracción.

---

## Capabilities

### New Capabilities

_(ninguna — `roles-permisos` ya existe como spec base)_

### Modified Capabilities

- `roles-permisos`: implementación completa de R-1, R-2 y R-3. Se añaden los scenarios de verificación de estado post-token, auditoría de 403, y cobertura de TDD al 80%.

---

## Impact

**Backend:**
- Nuevo `UserStatusFilter` en `com.padelpro.auth.infrastructure.web.filter`.
- Nuevos beans `AccessDeniedHandler` y `AuthenticationEntryPoint` en `com.padelpro.auth.infrastructure.config`.
- Extensión de `SecurityFilterChain` con las nuevas reglas de rutas webhook.
- Nueva interface `ResourceOwnershipPort` en `com.padelpro.auth.domain.port.in` (stub vacío — se implementará en `reservas` y `pagos-redsys`).
- `HexagonalArchitectureTest`: nueva regla ArchUnit para verificaciones de propiedad.
- `pom.xml`: plugin JaCoCo con umbral 80% y exclusiones.

**Frontend:**
- Sin cambios de implementación. Las respuestas `ErrorResponse` uniformes para 401/403 pueden mejorar el manejo de errores en los componentes existentes (`LoginPage`, `MiPerfilPage`).

**Dependencias:**
- Requiere `auth-local` (JwtAuthFilter, AuditLogRepositoryPort).
- Requiere `usuarios` (UserRepositoryPort para re-verificar status).
- Desbloquea: `configuracion-club`, `disponibilidad-pistas`, `reservas`, `auth-otp-telegram` (todos asumen que la seguridad transversal está completa).

---

## Alcance fuera del change

| Capacidad excluida | Change futuro |
|---|---|
| Implementación de RN-AUTH-01/02/03/04 en lógica de negocio | `reservas`, `pagos-redsys` |
| UI de gestión de roles (panel admin) | `administracion-club` (Fase 2) |
| Refresh token revocación al desactivar usuario | `auth-password-reset` |
| Auditoría de intentos de acceso por bot Telegram | `auth-otp-telegram` |
