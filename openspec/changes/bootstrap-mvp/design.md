# Design: Bootstrap MVP — auth-local

**Change slug:** `bootstrap-mvp`
**Capability:** `auth-local`
**Estado:** proposed
**Fuente autoritativa de referencia:** `docs/security-design.md`, `docs/openapi.yaml`, `docs/data-model.md`

---

## Decisión 1 — Algoritmo de hashing de contraseñas

**Decisión:** BCrypt con factor de coste 12.

**Justificación:**
- RN-AUTH-08 lo especifica explícitamente: *"hash BCrypt cost 12"*.
- BCrypt está recomendado por OWASP ASVS v4 §2.4 como opción adecuada cuando el hardware es predecible (instalaciones On-Premise de gama media).
- Cost 12 produce ~300 ms de hash en hardware moderno de servidor, suficiente para ralentizar ataques de fuerza bruta sin impactar UX (el usuario percibe el login como inmediato).

**Alternativa documentada:** Argon2id (OWASP ASVS §2.4 lo prefiere en hardware moderno). Si el equipo decide migrar, el campo `password_hash` es opaco para la API; el cambio es interno al servicio de autenticación y no rompe el contrato.

**Implementación en Spring Boot:**
- Dependencia: `spring-security-crypto` (incluida en `spring-boot-starter-security`).
- Bean: `BCryptPasswordEncoder(12)`.
- Nunca registrar en logs el valor plano ni el hash (RN-RGPD-04).

---

## Decisión 2 — Formato del token de sesión

**Decisión:** JWT firmado con HS256 (HMAC-SHA256), doble token (access + refresh).

| Parámetro | Valor | Fuente |
|---|---|---|
| Algoritmo de firma | HS256 | `docs/security-design.md §2.2` |
| Duración access token | 15 minutos | RN-AUTH-09 / `security-design.md §2.3` |
| Duración refresh token | 7 días | RN-AUTH-09 |
| Almacenamiento access | Memoria JavaScript (no localStorage, no sessionStorage) | RN-AUTH-09 |
| Almacenamiento refresh | Cookie httpOnly, SameSite=Strict, Secure | RN-AUTH-09 |
| Claims del access token | `sub` (user_id), `role`, `iat`, `exp` | `openapi.yaml` — securitySchemes `bearerAuth` |
| Claims del refresh token | `sub` (user_id), `jti` (UUID v4), `iat`, `exp` | Decisión interna |

**Por qué HS256 y no RS256:**
- PadelPro es una instalación On-Premise monolítica. No hay múltiples servicios que necesiten verificar tokens con una clave pública distribuida.
- HS256 es más simple de gestionar en un entorno Docker Compose donde el secreto puede almacenarse como variable de entorno.
- RS256 se justifica cuando hay microservicios o terceros que necesitan verificar tokens sin acceso al secreto. No es el caso en v1.0.

**Nota de conflicto documentado:** `README.md §3.7` indica 8 horas para el access token. La fuente autoritativa es `docs/security-design.md §2.3` (15 minutos). Se usa 15 minutos. Seguimiento: `openspec/AGENTS.md §6`.

---

## Decisión 3 — Formato de respuesta de error en endpoints de auth

**Decisión:** Respuesta genérica anti-enumeración.

```json
{
  "error": "AUTH_INVALID_CREDENTIALS",
  "message": "Credenciales inválidas",
  "timestamp": "2026-05-23T10:00:00Z"
}
```

**Regla:** Los endpoints `POST /api/auth/login` y `POST /api/auth/register` **nunca** revelan si el email ya existe en el sistema. El mensaje de error es idéntico para:
- Email no registrado + contraseña cualquiera → 401
- Email registrado + contraseña incorrecta → 401
- Cuenta desactivada → 401 (mismo mensaje; no revelar estado de cuenta)

**Excepción justificada:** El registro (`POST /api/auth/register`) devuelve 409 Conflict si el email ya existe, porque el usuario *debe* saber que ya tiene cuenta para no crear un duplicado. Sin embargo, el cuerpo del 409 solo dice `"error": "EMAIL_ALREADY_REGISTERED"` sin confirmar si la cuenta está activa o desactivada.

Este diseño cumple con RN-RGPD-03 (las respuestas no exponen datos de otros usuarios) y la regla anti-enumeración de OWASP API Security Top 10 (API3:2023).

**Escenario UX:** La pantalla `01-login.html` muestra un único mensaje de error genérico. La pantalla `08-crear-cuenta.html` muestra un mensaje diferente si el email ya existe (409), lo que es aceptable porque es el flujo de registro propio del usuario.

---

## Decisión 4 — Política de contraseñas mínima

**Decisión** (propuesta inicial — requiere validación humana):

| Criterio | Valor | Fuente |
|---|---|---|
| Longitud mínima | 8 caracteres | RN-AUTH-08 |
| Longitud máxima | 128 caracteres | RN-AUTH-08 |
| Mayúscula requerida | Sí (≥1) | RN-AUTH-08 |
| Número requerido | Sí (≥1) | RN-AUTH-08 |
| Símbolo requerido | **No** — validado con `lcasadov` el 2026-05-23 | RN-AUTH-08; símbolo descartado |

**Implementación:** Regex de validación en el servicio de dominio (no en el controlador). La validación es responsabilidad del backend; el frontend puede replicarla para UX pero el backend es la única fuente de verdad.

---

## Decisión 5 — Persistencia en base de datos

**Tabla `users`** (Flyway V1 — ver `docs/data-model.md` para DDL completo):

| Columna | Tipo | Restricción | Notas |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | Clave interna. No expuesta en JWT (se usa en `sub` como string). |
| `login` | `VARCHAR(100)` | UNIQUE NOT NULL | Alias de email. Lowercase enforced. |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt cost 12. Nunca en logs. |
| `first_name` | `VARCHAR(100)` | NOT NULL | Requerido en registro (UX `08-crear-cuenta`). |
| `last_name` | `VARCHAR(100)` | NOT NULL | Requerido en registro. |
| `email` | `VARCHAR(255)` | UNIQUE NOT NULL | Igual que `login` en v1.0; separado para facilitar migración futura. |
| `role` | `ENUM('ADMIN','USER')` | NOT NULL DEFAULT 'USER' | Solo dos roles en v1.0. |
| `status` | `ENUM('PENDING','ACTIVE','INACTIVE')` | NOT NULL DEFAULT 'ACTIVE' | `PENDING` si se requiere verificación (change futuro). |
| `registered_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | Inmutable. |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | Actualizado por trigger. |
| `last_login_at` | `TIMESTAMPTZ` | NULLABLE | Actualizado en cada login exitoso. |

**Nota de diseño:** `status = 'PENDING'` existe en el schema para el change de verificación de email, pero en `bootstrap-mvp` todos los registros nacen como `ACTIVE`. El campo se incluye ahora para evitar migraciones rupturistas más adelante.

**Tabla `refresh_tokens`** (Flyway V2 — ver `docs/data-model.md`):

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `user_id` | `BIGINT` | FK → `users.id` ON DELETE CASCADE |
| `token_hash` | `VARCHAR(64)` | SHA-256 del refresh token. Nunca almacenar el token plano. |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL |
| `revoked` | `BOOLEAN` | DEFAULT FALSE. Permite revocar sesiones. |
| `ip_address` | `INET` | Para auditoría. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() |

---

## Decisión 6 — Rate limiting de endpoints públicos

**Decisión:** Throttle por IP implementado en la capa de Spring Security o mediante un filtro Servlet.

| Endpoint | Límite | Fuente |
|---|---|---|
| `POST /api/auth/login` | 5 requests/min/IP | RN-SEC-01 |
| `POST /api/auth/register` | 3 requests/min/IP | RN-SEC-01 |
| `POST /api/auth/password/solicitar-reset` | 3 requests/min/IP | RN-SEC-01 (change futuro) |

**Respuesta al superar el límite:** HTTP 429 Too Many Requests con cabecera `Retry-After: <segundos>`.

**Implementación recomendada:** Bucket4j (biblioteca Java compatible con Spring Boot 3.x) con almacenamiento en memoria para v1.0. Si hay múltiples instancias (Fase 3), migrar a Redis. En v1.0 (Docker Compose, instancia única) la memoria es suficiente.

**Nota:** El bloqueo de cuenta por fallos repetidos (RN-AUTH-06: 10 fallos en 10 min → 15 min de bloqueo) es distinto del rate limiting por IP y se declara en el change `auth-lockout`. No forma parte de este change.

---

## Decisiones aplazadas

Las siguientes decisiones se cierran en changes posteriores:

| Decisión | Change responsable |
|---|---|
| Flujo de recuperación de contraseña (endpoint, OTP, expiración) | `auth-password-reset` |
| Verificación de email al registrarse (¿obligatoria en v1.0?) | `auth-email-verification` |
| Bloqueo de cuenta tras N fallos consecutivos (RN-AUTH-06) | `auth-lockout` |
| Política de sesiones simultáneas (¿cuántos refresh tokens activos por usuario?) | `auth-session-management` |
| Expiración por inactividad (distinta de la expiración absoluta del token) | `auth-session-management` |
| Vinculación con Telegram y OTP para operaciones críticas | `auth-otp-telegram` |
| Rotación del secreto JWT (¿cómo se invalidan tokens existentes?) | `auth-session-management` |
