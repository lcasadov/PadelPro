# Capability: auth-local — Change bootstrap-mvp

> **Tipo de artefacto:** spec de change (delta sobre `openspec/specs/auth-local/spec.md`).
> Todos los requirements de este fichero están marcados `[AÑADIDO]` porque son la primera implementación de la capability.
> Al archivar este change (`/opsx:archive bootstrap-mvp`), estos requirements se fusionan con el spec base.

**Change slug:** `bootstrap-mvp`
**Capability:** `auth-local`
**Issue GitHub:** #76

---

## Propósito

Permite a un usuario crear una cuenta y autenticarse con email y contraseña, obteniendo un token JWT (HS256) para llamadas posteriores a la API. Es la capability mínima que habilita el resto de la Fase 1.

---

## Roles que consumen esta capability

| Rol | Operación permitida |
|---|---|
| `USER` (jugador registrado) | Puede hacer login, obtener token y acceder a recursos propios. |
| No autenticado (antes del registro) | Puede registrarse (`POST /api/auth/register`) y hacer login (`POST /api/auth/login`). Tras el registro, el usuario obtiene rol `USER`. |

> Los roles `ADMIN` y `USER` son los únicos roles válidos en v1.0 (AGENTS.md §4 regla 3). El concepto de "usuario no autenticado" no es un rol del sistema; es el estado previo al registro.

---

## Reglas de negocio implicadas

| Código | Descripción |
|---|---|
| **RN-AUTH-08** | Contraseña: mínimo 8 caracteres + 1 mayúscula + 1 número; máximo 128 caracteres; hash BCrypt cost 12. |
| **RN-AUTH-09** | Access token 15 min almacenado en memoria JS; refresh token 7 días en cookie httpOnly. |
| **RN-RGPD-03** | Las respuestas de error no exponen datos de otros usuarios (anti-enumeración). |
| **RN-RGPD-04** | Los logs no contienen contraseñas, tokens JWT, códigos OTP ni datos de tarjeta. |
| **RN-SEC-01** | Rate limiting: login 5/min/IP, registro 3/min/IP. |

---

## Endpoints cubiertos por este change

| Método | Path | Autenticación |
|---|---|---|
| `POST` | `/api/auth/register` | Pública |
| `POST` | `/api/auth/login` | Pública |

> Los endpoints `/api/auth/refresh`, `/api/auth/logout` y `/api/auth/password/*` se declaran en changes posteriores (`auth-session-management`, `auth-password-reset`).

---

## Requirements

### R-1 — Registro de usuario con email único `[AÑADIDO]`

Un usuario no autenticado puede crear una cuenta proporcionando su nombre, apellido, email y contraseña. El email debe ser único en el sistema. La contraseña debe cumplir la política mínima (RN-AUTH-08). El sistema crea la cuenta con rol `USER` y estado `ACTIVE`.

#### Scenarios

**Scenario R-1.1 — Registro válido**
```
GIVEN un usuario no autenticado
  AND los campos {first_name, last_name, email, password} son válidos
  AND el email no existe en el sistema
WHEN el usuario envía POST /api/auth/register con esos campos
THEN el sistema responde 201 Created
  AND el body contiene {id, email, role: "USER"}
  AND el usuario queda persistido en la tabla users con status=PENDING
  AND se genera una entrada en audit_log con action=USER_REGISTERED
```

**Scenario R-1.2 — Email ya registrado**
```
GIVEN un usuario no autenticado
  AND el email proporcionado ya existe en la tabla users
WHEN el usuario envía POST /api/auth/register
THEN el sistema responde 409 Conflict
  AND el body contiene {error: "EMAIL_ALREADY_REGISTERED"}
  AND no se crea ningún registro nuevo en users
  AND la respuesta no indica si la cuenta existente está activa o inactiva
```

**Scenario R-1.3a — Contraseña demasiado corta**
```
GIVEN un usuario no autenticado
  AND la contraseña tiene menos de 8 caracteres
WHEN el usuario envía POST /api/auth/register
THEN el sistema responde 400 Bad Request
  AND el body contiene {error: "INVALID_PASSWORD", details: ["MIN_LENGTH_8"]}
  AND no se crea ningún registro en users
```

**Scenario R-1.3b — Contraseña sin mayúscula**
```
GIVEN un usuario no autenticado
  AND la contraseña tiene 8 o más caracteres
  AND la contraseña no contiene ninguna letra mayúscula
WHEN el usuario envía POST /api/auth/register
THEN el sistema responde 400 Bad Request
  AND el body contiene {error: "INVALID_PASSWORD", details: ["REQUIRES_UPPERCASE"]}
  AND no se crea ningún registro en users
```

**Scenario R-1.3c — Contraseña sin número**
```
GIVEN un usuario no autenticado
  AND la contraseña tiene 8 o más caracteres
  AND la contraseña contiene al menos una letra mayúscula
  AND la contraseña no contiene ningún dígito numérico
WHEN el usuario envía POST /api/auth/register
THEN el sistema responde 400 Bad Request
  AND el body contiene {error: "INVALID_PASSWORD", details: ["REQUIRES_NUMBER"]}
  AND no se crea ningún registro en users
```

**Scenario R-1.4 — Campo obligatorio ausente**
```
GIVEN un usuario no autenticado
  AND el body de la petición omite al menos uno de {first_name, last_name, email, password}
WHEN el usuario envía POST /api/auth/register
THEN el sistema responde 400 Bad Request
  AND el body identifica el campo faltante
  AND no se crea ningún registro en users
```

---

### R-2 — Login con credenciales válidas `[AÑADIDO]`

Un usuario registrado puede autenticarse con su email y contraseña. Si las credenciales son correctas y la cuenta está `ACTIVE`, el sistema emite un access token JWT y un refresh token (cookie httpOnly). Si las credenciales son incorrectas o el email no existe, el sistema responde 401 con el mismo mensaje genérico (anti-enumeración, RN-RGPD-03). Si las credenciales son correctas pero la cuenta está en estado `PENDING` o `INACTIVE`, el sistema responde 403.

#### Scenarios

**Scenario R-2.1 — Login con credenciales válidas**
```
GIVEN un usuario con email=user@example.com existe en el sistema con status=ACTIVE
  AND la contraseña proporcionada es correcta
WHEN el usuario envía POST /api/auth/login con {email, password}
THEN el sistema responde 200 OK
  AND el body contiene {access_token: "<JWT>", token_type: "Bearer", expires_in: 900}
  AND la respuesta incluye el header Set-Cookie con refresh_token; HttpOnly; SameSite=Strict; Secure; Max-Age=604800
  AND se actualiza users.last_login_at con el timestamp actual
```

**Scenario R-2.2 — Email no registrado (anti-enumeración)**
```
GIVEN el email proporcionado no existe en la tabla users
WHEN el usuario envía POST /api/auth/login con {email, password}
THEN el sistema responde 401 Unauthorized
  AND el body contiene {error: "AUTH_INVALID_CREDENTIALS", message: "Credenciales inválidas"}
  AND la respuesta es indistinguible del Scenario R-2.3
  AND no se genera ninguna entrada en audit_log con datos del email (para no exponer si existe)
```

**Scenario R-2.3 — Contraseña incorrecta**
```
GIVEN un usuario con email=user@example.com existe en el sistema con status=ACTIVE
  AND la contraseña proporcionada es incorrecta
WHEN el usuario envía POST /api/auth/login con {email, password}
THEN el sistema responde 401 Unauthorized
  AND el body contiene {error: "AUTH_INVALID_CREDENTIALS", message: "Credenciales inválidas"}
  AND el tiempo de respuesta no revela si el email existe (se ejecuta el hash BCrypt igualmente)
  AND se genera una entrada en audit_log con action=LOGIN_FAILURE, user_id=<id del usuario>, ip_address=<IP>
```

**Scenario R-2.4 — Cuenta en estado PENDING o INACTIVE `[AÑADIDO]`**
```
GIVEN un usuario con email=user@example.com existe en el sistema
  AND la contraseña proporcionada es correcta
  AND el status del usuario es PENDING o INACTIVE
WHEN el usuario envía POST /api/auth/login con {email, password}
THEN el sistema responde 403 Forbidden
  AND el body contiene {error: "ACCOUNT_NOT_ACTIVE", message: "Tu cuenta aún no está activada. Contacta con el administrador."}
  AND no se emite ningún token JWT
  AND se genera una entrada en audit_log con action=LOGIN_FAILURE, user_id=<id del usuario>, ip_address=<IP>
```

---

### R-3 — Emisión y validación de JWT `[AÑADIDO]`

El sistema emite access tokens JWT firmados con HS256. El token es válido durante 15 minutos. El sistema rechaza tokens expirados o manipulados.

#### Scenarios

**Scenario R-3.1 — Token recién emitido es válido**
```
GIVEN un access token JWT emitido en el login del Scenario R-2.1
  AND el token tiene menos de 15 minutos de antigüedad
WHEN el cliente envía el token en el header Authorization: Bearer <token> a cualquier endpoint protegido
THEN el sistema acepta el token
  AND procesa la petición con el rol extraído del claim "role"
```

**Scenario R-3.2 — Token expirado se rechaza**
```
GIVEN un access token JWT cuyo campo exp es anterior al momento actual
WHEN el cliente envía ese token en el header Authorization: Bearer <token>
THEN el sistema responde 401 Unauthorized
  AND el body contiene {error: "TOKEN_EXPIRED"}
  AND no se procesa la operación solicitada
```

**Scenario R-3.3 — Token con firma manipulada se rechaza**
```
GIVEN un access token JWT cuya firma ha sido alterada (payload modificado o firma cambiada)
WHEN el cliente envía ese token en el header Authorization: Bearer <token>
THEN el sistema responde 401 Unauthorized
  AND el body contiene {error: "TOKEN_INVALID"}
  AND no se procesa la operación solicitada
  AND se genera una entrada en audit_log con action=TOKEN_TAMPERED, ip_address=<IP>
```

---

### R-4 — Rate limiting de endpoints públicos de auth `[AÑADIDO]`

Los endpoints públicos `POST /api/auth/login` y `POST /api/auth/register` están sujetos a throttling por IP para prevenir ataques de fuerza bruta (RN-SEC-01).

#### Scenarios

**Scenario R-4.1 — Peticiones por debajo del umbral se procesan**
```
GIVEN una IP que ha enviado 4 peticiones a POST /api/auth/login en el último minuto
WHEN esa IP envía una 5ª petición a POST /api/auth/login
THEN el sistema procesa la petición normalmente (200 o 401 según las credenciales)
  AND no se devuelve ningún código de error relacionado con el límite
```

**Scenario R-4.2 — Al superar el umbral se devuelve 429**
```
GIVEN una IP que ha enviado 5 peticiones a POST /api/auth/login en el último minuto
WHEN esa IP envía una 6ª petición a POST /api/auth/login
THEN el sistema responde 429 Too Many Requests
  AND la respuesta incluye el header Retry-After con los segundos hasta que se libera el cupo
  AND el body contiene {error: "RATE_LIMIT_EXCEEDED"}
  AND no se procesa la autenticación
```

**Scenario R-4.3 — Rate limiting de registro**
```
GIVEN una IP que ha enviado 3 peticiones a POST /api/auth/register en el último minuto
WHEN esa IP envía una 4ª petición a POST /api/auth/register
THEN el sistema responde 429 Too Many Requests con Retry-After
```

---

### R-5 — Auditoría de intentos de login `[AÑADIDO]`

Cada intento de login (exitoso o fallido) genera una entrada en la tabla `audit_log` con timestamp, dirección IP y resultado. La contraseña nunca se registra ni se insinúa en los logs (RN-RGPD-04).

#### Scenarios

**Scenario R-5.1 — Login exitoso genera entrada de auditoría**
```
GIVEN un usuario autentica correctamente (Scenario R-2.1)
THEN el sistema registra en audit_log:
  - action = 'LOGIN_SUCCESS'
  - user_id = <id del usuario autenticado>
  - ip_address = <IP del cliente>
  - created_at = <timestamp actual>
  - details = NULL (no se almacena token ni contraseña)
```

**Scenario R-5.2 — Login fallido con usuario existente genera entrada de auditoría**
```
GIVEN un usuario envía credenciales incorrectas para un email que existe (Scenario R-2.3)
THEN el sistema registra en audit_log:
  - action = 'LOGIN_FAILURE'
  - user_id = <id del usuario cuyo email se intentó>
  - ip_address = <IP del cliente>
  - created_at = <timestamp actual>
  - details = NULL (sin contraseña, sin token)
```

**Scenario R-5.3 — Login fallido con email inexistente NO expone el email en audit_log**
```
GIVEN un usuario envía credenciales con un email que no existe en el sistema (Scenario R-2.2)
THEN el sistema registra en audit_log:
  - action = 'LOGIN_FAILURE'
  - user_id = NULL
  - ip_address = <IP del cliente>
  - created_at = <timestamp actual>
  - details = NULL
  AND el email enviado NO se almacena en audit_log (para no acumular datos de personas no registradas)
```

---

### R-6 — Administrador inicial en un despliegue nuevo `[AÑADIDO — acceso-cuenta-prod]`

El sistema DEBE garantizar que exista al menos un administrador `ACTIVE` tras un despliegue nuevo, creándolo de forma idempotente a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD` del entorno cuando no exista ningún administrador, con la contraseña cifrada en BCrypt (RN-AUTH-07).

- **GIVEN** BD sin ningún ADMIN y `ADMIN_EMAIL`/`ADMIN_PASSWORD` definidas → **WHEN** el backend arranca → **THEN** se crea un ADMIN `ACTIVE` (BCrypt).
- **GIVEN** ya existe un ADMIN → **WHEN** arranca de nuevo → **THEN** no crea otro (idempotente).
- **GIVEN** sin variables definidas → **WHEN** arranca → **THEN** no falla y no crea nada.

### R-7 — Acceso provisional de 2 días para cuentas pendientes `[AÑADIDO — acceso-cuenta-prod]`

El sistema DEBE permitir iniciar sesión a un usuario `PENDING` durante las 48 horas siguientes a su registro (sobre `registered_at`). Pasado el plazo sin aprobación → `403 ACCOUNT_NOT_ACTIVE`. La aprobación deja la cuenta `ACTIVE` permanente; una cuenta `INACTIVE` no tiene gracia y se bloquea siempre.

- `PENDING` < 48 h → login 200 (uso provisional, también en peticiones autenticadas posteriores).
- `PENDING` > 48 h → 403 `ACCOUNT_NOT_ACTIVE`.
- `INACTIVE` → 403 siempre.

### R-8 — Distinción entre cuenta no activa y credenciales inválidas `[AÑADIDO — acceso-cuenta-prod]`

El sistema DEBE diferenciar cuenta no activa (403 `ACCOUNT_NOT_ACTIVE`) de credenciales inválidas (401 `AUTH_INVALID_CREDENTIALS`), y la interfaz web DEBE mostrar mensajes distintos para cada caso.

### R-9 — Confirmación tras el registro `[AÑADIDO — acceso-cuenta-prod]`

Tras un registro correcto, la interfaz web DEBE mostrar una confirmación indicando que la cuenta queda pendiente de aprobación del administrador y que dispone de acceso provisional durante 2 días.

### R-10 — Cambio de contraseña forzado tras un restablecimiento `[AÑADIDO — acceso-cuenta-prod]`

Cuando una cuenta tiene `must_change_password = true` (tras un reset del administrador o un alta directa), el login DEBE señalarlo en la respuesta (`must_change_password`), la interfaz DEBE exigir el cambio antes del uso normal (`POST /api/usuarios/me/password`), y el flag DEBE limpiarse al cambiarla.

### R-11 — Recuperación de contraseña gobernada por el administrador `[AÑADIDO — acceso-cuenta-prod]`

La interfaz web DEBE ofrecer, desde el login ("¿Olvidaste la contraseña?"), una pantalla que dirija al usuario a contactar con el administrador del club; el restablecimiento efectivo lo realiza el administrador (ver capability `usuarios`). NO existe reset self-service por email/token en esta fase.

### R-12 — Renovación de access token vía refresh token `[AÑADIDO — auth-session-refresh]`

El sistema DEBE exponer `POST /api/auth/refresh` que, a partir de la cookie httpOnly `refresh_token`, emite un nuevo access token JWT (15 min) y **rota** el refresh token de forma atómica: valida que exista (por hash), no esté expirado ni revocado, revoca el usado (compare-and-set) y emite uno nuevo con nueva ventana de 7 días en una nueva cookie `refresh_token; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh`. El endpoint NO exige access token y está sujeto al mismo rate limiting que el resto de auth público (RN-AUTH-04). El refresh token se almacena solo como hash SHA-256; el raw únicamente en la cookie (RN-RGPD-04).

#### Scenarios

**Scenario R-12.1 — Refresh con cookie válida rota el token**
```
GIVEN una cookie refresh_token válida (no expirada, no revocada)
WHEN el cliente envía POST /api/auth/refresh
THEN el sistema responde 200 con {access_token, token_type: "Bearer", expires_in: 900}
  AND setea un nuevo refresh_token en Set-Cookie
  AND el refresh token anterior queda revocado
```

**Scenario R-12.2 — Cookie ausente, expirada o revocada se rechaza**
```
GIVEN una petición a POST /api/auth/refresh sin cookie, o con un refresh_token expirado/revocado
WHEN el sistema procesa la petición
THEN responde 401 con {error: "AUTH_REFRESH_INVALID"}
  AND no emite ningún token
```

**Scenario R-12.3 — El refresh usado no se puede reutilizar (ni en carrera)**
```
GIVEN un refresh_token que se usa con éxito
WHEN se intenta usar de nuevo el mismo valor (secuencial o concurrentemente)
THEN el segundo intento responde 401 (la revocación compare-and-set decide un único ganador)
```

### R-13 — Renovación silenciosa de sesión en el cliente `[AÑADIDO — auth-session-refresh]`

El cliente web DEBE renovar la sesión de forma transparente: ante un 401 en una petición autenticada, intenta `POST /api/auth/refresh` una sola vez y, si tiene éxito, reintenta la petición original con el nuevo access token; si el refresh falla, limpia la sesión y redirige a login. Peticiones 401 concurrentes comparten una única renovación en vuelo (single-flight) y el endpoint de refresh se excluye para evitar bucles. El access token renovado vive solo en memoria (RN-AUTH-09).

#### Scenarios

**Scenario R-13.1 — Renovación transparente ante 401**
```
GIVEN una petición autenticada que recibe 401 por access token caducado y refresh vigente
WHEN el interceptor del cliente procesa la respuesta
THEN renueva el token y reintenta la petición original, que se completa sin intervención del usuario
```

**Scenario R-13.2 — Refresh fallido lleva a login**
```
GIVEN una petición que recibe 401 y el refresh también falla
WHEN el interceptor agota la renovación
THEN limpia la sesión y redirige a la pantalla de login, sin bucle sobre /api/auth/refresh
```

---

## Mockups asociados

Los siguientes mockups ilustran la experiencia de usuario para este change. Todos los links son relativos desde `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md`.

### Pantallas

| Pantalla | Fichero | Permisos | Momento en el flujo |
|---|---|---|---|
| Splash / Bienvenida | [`07-splash.html`](../../../../../docs/ux/mockups/07-splash.html) | Pública | Primer punto de entrada. CTAs "Iniciar sesión" y "Crear cuenta". |
| Login | [`01-login.html`](../../../../../docs/ux/mockups/01-login.html) | Pública | Campos email + contraseña. Mensaje de error genérico en 401 (R-2.2 y R-2.3). |
| Crear cuenta (registro) | [`08-crear-cuenta.html`](../../../../../docs/ux/mockups/08-crear-cuenta.html) | Pública | Campos nombre, apellido, email, contraseña. Feedback de política de contraseñas (R-1.3). |

### Flujos relacionados

- **Flujo 1 · Onboarding y autenticación** → [`docs/ux/flujos.md`](../../../../../docs/ux/flujos.md)

El flujo muestra:
```
07-splash → ¿Tiene cuenta? → Sí → 01-login → OK → 02-home-jugador
                            → No → 08-crear-cuenta → POST /api/auth/register 201 → pantalla de confirmación (cuenta pendiente de aprobación)
```

Índice completo de pantallas: [`docs/ux/README.md`](../../../../../docs/ux/README.md)

### Notas de UX derivadas de los requirements

> **Anti-enumeración (R-2.2 / R-2.3):** La pantalla `01-login.html` muestra un único mensaje de error genérico ("Credenciales inválidas") tanto si el email no existe como si la contraseña es incorrecta. El frontend no puede diferenciar los casos porque el backend devuelve el mismo 401 en ambos.

> **Política de contraseñas (R-1.3):** La pantalla `08-crear-cuenta.html` puede mostrar un indicador de fortaleza en tiempo real. Sin embargo, la validación autoritativa ocurre en el backend; si el frontend la omite, el 400 debe ser legible (campo `details` con criterio fallido).

> **Cookie httpOnly (R-2.1):** El refresh token no es accesible desde JavaScript. El frontend no necesita gestionarlo explícitamente; el navegador lo envía automáticamente en las peticiones a `/api/auth/refresh`. El access token sí debe gestionarse en memoria JS (T-029).
