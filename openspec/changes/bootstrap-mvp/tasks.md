# Tasks: Bootstrap MVP

**Change slug:** `bootstrap-mvp`
**Issue GitHub:** #76
**Rama de trabajo:** `feat/openspec-bootstrap-mvp`
**Agentes responsables:** `openspec-curator` (DOCS), `backend-architect` (BACKEND), `frontend-engineer` (FRONTEND)

> Cada tarea lleva un identificador `T-NNN` para ser referenciada en commits (`T-001`) y en comentarios de PR (`Closes T-001`).
> El agente que ejecuta la tarea marca el checkbox `[x]` cuando termina.

---

## Bloque DOCS

Ejecutar antes que BACKEND y FRONTEND. Valida que el andamiaje OpenSpec está completo.

- [x] **T-001** — Verificar que `openspec/project.md` existe y es coherente con el stack y roles actuales (ADMIN/USER en v1.0). Sin modificaciones si ya es correcto.
- [x] **T-002** — Verificar que `openspec/AGENTS.md` existe, contiene el orden de lectura obligatorio y las reglas para agentes. Sin modificaciones si ya es correcto.
- [x] **T-003** — Crear `openspec/changes/bootstrap-mvp/proposal.md` con motivación, alcance, criterios de aceptación, riesgos e impacto en otros changes.
- [x] **T-004** — Crear `openspec/changes/bootstrap-mvp/design.md` con las 6 decisiones de diseño (hashing, JWT, errores, política de contraseñas, persistencia, rate limiting) y la sección "Decisiones aplazadas".
- [x] **T-005** — Crear `openspec/changes/bootstrap-mvp/tasks.md` (este fichero).
- [x] **T-006** — Crear `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md` con requirements R-1 a R-5, todos marcados `[AÑADIDO]`, con scenarios Given/When/Then y sección `## Mockups asociados`.
- [x] **T-007** — Validar que los 3 enlaces relativos en el spec hacia `docs/ux/mockups/` resuelven a ficheros existentes: `01-login.html`, `07-splash.html`, `08-crear-cuenta.html`.
- [x] **T-008** — Crear el Issue GitHub #76 (ya creado) y añadirlo al Project v2 (`lcasadov/projects/1`) con status `In Review`.

---

## Bloque BACKEND

Ejecutar en la rama `feature/<N>-auth-local` una vez que el change pase a `approved`. Requiere que T-001 a T-007 estén completos.

Agente responsable: **`backend-architect`**. Lee `docs/openapi.yaml`, `docs/data-model.md`, `docs/security-design.md` y este `tasks.md` antes de empezar.

### Modelo de dominio

- [x] **T-009** — Crear entidad JPA `User` con todos los campos del diseño (ver `design.md §Decisión 5`). Módulo: `domain/`.
- [x] **T-010** — Crear repositorio `UserRepository` (Spring Data JPA) con método `findByEmail(String email)`. Módulo: `infrastructure/`.
- [x] **T-011** — Crear entidad JPA `RefreshToken` y repositorio `RefreshTokenRepository` con métodos `findByTokenHash`, `revokeAll(Long userId)`. Módulo: `infrastructure/`.

### Servicios de aplicación

- [x] **T-012** — Implementar `RegistrationService.register(RegisterCommand cmd)`: validar política de contraseñas (RN-AUTH-08), verificar unicidad de email (409 si ya existe), hash BCrypt cost 12, persistir `User` con `status=PENDING` (el ADMIN activa con `PATCH /api/admin/usuarios/{id}/aprobar`), publicar evento `UserRegistered`. Módulo: `application/`.
- [x] **T-013** — Implementar `AuthService.login(LoginCommand cmd)`: cargar usuario por email, verificar `BCryptPasswordEncoder.matches()`, generar access token JWT HS256 (claims: `sub`, `role`, `iat`, `exp`), generar refresh token (UUID v4), persistir hash SHA-256 del refresh token en `refresh_tokens`, actualizar `last_login_at`. Módulo: `application/`.
- [x] **T-014** — Implementar `JwtService.generateAccessToken(User user)` y `JwtService.validateToken(String token)`. Algoritmo HS256, duración 15 min (RN-AUTH-09). Módulo: `application/` o `infrastructure/`.

### API REST

- [x] **T-015** — Implementar `AuthController` con dos endpoints (ver `docs/openapi.yaml` para contrato exacto):
  - `POST /api/auth/register` → 201 con body `{id, email, role}` o 409 si email duplicado o 400 si política de contraseñas fallida.
  - `POST /api/auth/login` → 200 con access token en body + refresh token en cookie httpOnly `Set-Cookie: refresh_token=...; HttpOnly; SameSite=Strict; Secure; Max-Age=604800` o 401 genérico.
- [x] **T-016** — Configurar Spring Security: endpoints `/api/auth/**` públicos (sin JWT requerido). Todos los demás endpoints requieren JWT válido.

### Persistencia y migraciones

- [x] **T-017** — Crear migración Flyway `V1__create_users_table.sql` con DDL de la tabla `users` (ver `docs/data-model.md` y `design.md §Decisión 5`). Incluir `CREATE EXTENSION IF NOT EXISTS btree_gist` si no está ya en V1.
- [x] **T-018** — Crear migración Flyway `V2__create_refresh_tokens_table.sql` con DDL de `refresh_tokens` (FK, índices).
- [x] **T-018b** — Crear migración Flyway `V3__create_audit_log_table.sql` con DDL de `audit_log` (user_id nullable, ON DELETE SET NULL, índices de auditoría). Ejecutado por `database-optimizer`.

### Rate limiting

- [x] **T-019** — Añadir dependencia Bucket4j al POM. Implementar filtro de rate limiting para `POST /api/auth/login` (5/min/IP) y `POST /api/auth/register` (3/min/IP). Respuesta 429 con header `Retry-After`. (RN-SEC-01)

### Auditoría

- [x] **T-020** — Registrar en `audit_log` cada intento de login (exitoso o fallido): `action='LOGIN_SUCCESS'` o `action='LOGIN_FAILURE'`, `user_id` (si el email existe, sino NULL), `ip_address`, `timestamp`. Sin exponer contraseña ni token. (RN-RGPD-04)

### Testing

- [x] **T-021** — Tests unitarios de `RegistrationService`: contraseña válida, contraseña inválida (política), email duplicado. Mockear `UserRepository`.
- [x] **T-022** — Tests unitarios de `AuthService`: login válido, email inexistente (mismo error 401), contraseña incorrecta (mismo error 401). Mockear `UserRepository`.
- [x] **T-023** — Tests unitarios de `JwtService`: token recién emitido es válido, token expirado lanza excepción, token con firma manipulada lanza excepción.
- [x] **T-024** — Tests de integración con Testcontainers (`@SpringBootTest` + PostgreSQL container) para `POST /api/auth/register` y `POST /api/auth/login`. Verificar: 201, 400, 401, 409, cookie httpOnly presente en login exitoso.
- [x] **T-025** — Test de integración de rate limiting: 6 peticiones en 1 min a `POST /api/auth/login` → la 6ª devuelve 429 con `Retry-After`.

---

## Bloque FRONTEND

Ejecutar en paralelo con BACKEND una vez que el contrato REST esté acordado (T-015 definido). Puede empezar con MSW mock del endpoint.

Agente responsable: **`frontend-engineer`**. Lee `docs/openapi.yaml` y `docs/ux/mockups/` antes de empezar.

### Pantallas

- [x] **T-026** — Implementar pantalla `07-splash` (splash screen con CTA "Iniciar sesión" y "Crear cuenta") alineada con el mockup `docs/ux/mockups/07-splash.html`. Rutas: `/` → redirige a `/login` si no hay token.
- [x] **T-027** — Implementar formulario de login alineado con mockup `docs/ux/mockups/01-login.html`. Campos: email, contraseña. Conectar a `POST /api/auth/login`. Mostrar error genérico en 401 (no revelar si el email existe).
- [x] **T-028** — Implementar formulario de registro alineado con mockup `docs/ux/mockups/08-crear-cuenta.html`. Campos: nombre, apellido, email, contraseña (con indicador de fortaleza). Conectar a `POST /api/auth/register`. Mostrar 409 si email duplicado, 400 si política de contraseñas fallida.

### Gestión de sesión

- [x] **T-029** — Persistir el access token JWT en memoria JavaScript (variable de módulo o contexto React). Nunca en localStorage ni sessionStorage.
- [x] **T-030** — Implementar guard de rutas privadas: redirige a `/login` si no hay access token en memoria.

### Testing

- [x] **T-031** — Tests unitarios Vitest para los formularios de login y registro (validación de campos, manejo de errores 401/409/400).
- [x] **T-032** — Tests de integración con MSW: mock de `POST /api/auth/login` y `POST /api/auth/register` con respuestas 200, 201, 400, 401, 409. Verificar que la UI reacciona correctamente.
