# Capability: auth-local

## Resumen

Gestiona el ciclo de autenticación local de PadelPro: registro de nuevos usuarios, inicio de sesión con login y contraseña, renovación del access token, cierre de sesión y recuperación de contraseña mediante OTP enviado por Telegram. Implementa el modelo de doble token (access + refresh) con JWT HS256.

## Fase

Fase 1

## Reglas de negocio implicadas

- **RN-AUTH-06**: Bloqueo de cuenta tras 10 fallos de login en 10 minutos (15 min de bloqueo), aplicado de forma independiente por IP y por usuario.
- **RN-AUTH-07**: OTP de 6 dígitos, TTL 10 min, máximo 3 intentos, almacenado como SHA-256, de un solo uso.
- **RN-AUTH-08**: Contraseña: mínimo 8 caracteres + 1 mayúscula + 1 número; máximo 128 caracteres; hash BCrypt cost 12.
- **RN-AUTH-09**: Access token 15 min almacenado en memoria JavaScript; refresh token 7 días en cookie httpOnly.
- **RN-RGPD-04**: Los logs no contienen contraseñas, tokens JWT, códigos OTP ni datos de tarjeta.
- **RN-SEC-01**: Rate limiting: login 5/min/IP, registro 3/min/IP, solicitud de reset 3/min/IP.

## Entidades implicadas

**users** (tabla `users`):
- `id`, `login`, `password_hash`, `first_name`, `last_name`, `phone`, `email`, `status` (`PENDING|ACTIVE|INACTIVE`), `role` (`ADMIN|USER`), `telegram_chat_id`, `registered_at`, `updated_at`

**refresh_tokens** (tabla `refresh_tokens`):
- `id`, `user_id`, `token_hash` (SHA-256 del token), `expires_at`, `revoked`, `ip_address`, `created_at`

**otp_codes** (tabla `otp_codes`):
- `id`, `user_id`, `code` (SHA-256), `type` (`PASSWORD_RESET`), `expires_at`, `used`, `created_at`

## Endpoints

Todos los endpoints de esta capability tienen `security: []` (no requieren JWT salvo `/auth/logout`).

| Método | Path | OperationId | Autenticación |
|---|---|---|---|
| `POST` | `/api/auth/login` | `login` | Pública |
| `POST` | `/api/auth/register` | `register` | Pública |
| `POST` | `/api/auth/refresh` | `refreshToken` | Pública (cookie httpOnly) |
| `POST` | `/api/auth/logout` | `logout` | JWT requerido |
| `POST` | `/api/auth/password/solicitar-reset` | `solicitarResetPassword` | Pública |
| `POST` | `/api/auth/password/confirmar-reset` | `confirmarResetPassword` | Pública |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| `POST /api/auth/login` | Permitido | Permitido | Permitido |
| `POST /api/auth/register` | No aplica | No aplica | Permitido |
| `POST /api/auth/refresh` | Permitido | Permitido | Permitido (con cookie) |
| `POST /api/auth/logout` | Permitido | Permitido | Denegado (401) |
| `POST /api/auth/password/solicitar-reset` | Permitido | Permitido | Permitido |
| `POST /api/auth/password/confirmar-reset` | Permitido | Permitido | Permitido |

## Requirements

### Requirement 1: Login con doble token

**El sistema DEBE autenticar al usuario con login y contraseña, devolver un access token JWT HS256 con TTL de 15 minutos y establecer un refresh token en cookie httpOnly con TTL de 7 días.**

#### Scenario: Login exitoso con credenciales válidas

- **GIVEN** un usuario existe en el sistema con `status=ACTIVE` y `role=USER`
- **AND** el usuario no está bloqueado por intentos fallidos
- **WHEN** se envía `POST /api/auth/login` con `login` y `password` correctos
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene un `accessToken` JWT HS256 válido con `exp` a 15 minutos vista
- **AND** la respuesta incluye `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh; Max-Age=604800`
- **AND** el token contiene los claims `sub`, `login`, `role`, `iat`, `exp`, `jti`

#### Scenario: Login fallido con contraseña incorrecta (timing-safe)

- **GIVEN** un usuario existe en el sistema con `status=ACTIVE`
- **WHEN** se envía `POST /api/auth/login` con `login` correcto y `password` incorrecto
- **THEN** el sistema responde con código `401`
- **AND** el cuerpo sigue el esquema `ErrorResponse` sin revelar si el login existe
- **AND** el sistema registra el intento fallido en el contador de bloqueo (IP + usuario)
- **AND** el tiempo de respuesta es similar al de un login exitoso (resistencia a timing attacks)

#### Scenario: Login bloqueado por exceso de intentos fallidos (RN-AUTH-06)

- **GIVEN** un usuario ha fallado 10 intentos de login en los últimos 10 minutos
- **WHEN** se envía `POST /api/auth/login` con cualquier contraseña
- **THEN** el sistema responde con código `429`
- **AND** el cuerpo indica que la cuenta está bloqueada temporalmente
- **AND** la respuesta incluye el tiempo restante de bloqueo (15 minutos)

#### Scenario: Login denegado para cuenta PENDING

- **GIVEN** un usuario existe con `status=PENDING` (auto-registro no aprobado)
- **WHEN** se envía `POST /api/auth/login` con credenciales correctas
- **THEN** el sistema responde con código `403`
- **AND** el mensaje indica que la cuenta está pendiente de aprobación

---

### Requirement 2: Registro de nuevo usuario

**El sistema DEBE permitir el auto-registro de nuevos usuarios. La cuenta se crea en estado PENDING hasta que un administrador la apruebe.**

#### Scenario: Registro exitoso con datos válidos

- **GIVEN** no existe ningún usuario con el mismo `login`, `email` ni `phone`
- **WHEN** se envía `POST /api/auth/register` con `login`, `password`, `firstName`, `lastName`, `email` y `phone` válidos
- **THEN** el sistema responde con código `201`
- **AND** el usuario se crea con `status=PENDING` y `role=USER`
- **AND** la contraseña se almacena como hash BCrypt con cost 12 (RN-AUTH-08)
- **AND** la contraseña en claro nunca aparece en logs ni en la respuesta

#### Scenario: Registro rechazado por login duplicado

- **GIVEN** ya existe un usuario con `login=jdoe`
- **WHEN** se envía `POST /api/auth/register` con `login=jdoe`
- **THEN** el sistema responde con código `409`
- **AND** el mensaje indica conflicto de login sin revelar datos del usuario existente

#### Scenario: Registro rechazado por contraseña débil (RN-AUTH-08)

- **GIVEN** el sistema tiene configurada la política de contraseñas
- **WHEN** se envía `POST /api/auth/register` con `password` que no cumple los requisitos (menos de 8 chars, sin mayúscula o sin número)
- **THEN** el sistema responde con código `400`
- **AND** el mensaje detalla qué requisito de contraseña no se cumple

---

### Requirement 3: Renovación y revocación de tokens

**El sistema DEBE permitir renovar el access token usando el refresh token almacenado en la cookie httpOnly, e invalidar el refresh token al hacer logout.**

#### Scenario: Renovación exitosa con refresh token válido

- **GIVEN** el usuario tiene un refresh token activo (no revocado, no expirado) en cookie
- **WHEN** se envía `POST /api/auth/refresh` con la cookie `refresh_token`
- **THEN** el sistema responde con código `200`
- **AND** el cuerpo contiene un nuevo `accessToken` JWT válido
- **AND** el refresh token en la cookie se mantiene (o se rota si está habilitada la rotación)

#### Scenario: Renovación fallida con refresh token revocado

- **GIVEN** el usuario ha hecho logout y su refresh token fue marcado como `revoked=true`
- **WHEN** se envía `POST /api/auth/refresh` con ese refresh token
- **THEN** el sistema responde con código `401`
- **AND** la cookie `refresh_token` se borra (Max-Age=0)

#### Scenario: Logout invalida el refresh token

- **GIVEN** el usuario tiene un access token válido y un refresh token activo
- **WHEN** se envía `POST /api/auth/logout` con el access token en `Authorization: Bearer`
- **THEN** el sistema responde con código `204`
- **AND** el refresh token se marca como `revoked=true` en base de datos
- **AND** la cookie `refresh_token` se borra (`Max-Age=0`)
- **AND** un intento posterior de `POST /api/auth/refresh` con ese token responde `401`

---

### Requirement 4: Recuperación de contraseña mediante OTP

**El sistema DEBE permitir restablecer la contraseña mediante un OTP enviado al Telegram personal del usuario. El OTP es de un solo uso con TTL de 10 minutos (RN-AUTH-07).**

#### Scenario: Solicitud de reset con email registrado

- **GIVEN** existe un usuario con `email=jdoe@example.com` y `telegram_chat_id` vinculado
- **WHEN** se envía `POST /api/auth/password/solicitar-reset` con ese email
- **THEN** el sistema responde con código `200` (respuesta neutral, no revela si el email existe)
- **AND** se genera un OTP de tipo `PASSWORD_RESET` almacenado como SHA-256 con TTL 10 min
- **AND** el OTP se envía al chat personal de Telegram del usuario

#### Scenario: Solicitud de reset con email no registrado (respuesta neutral)

- **GIVEN** no existe ningún usuario con el email proporcionado
- **WHEN** se envía `POST /api/auth/password/solicitar-reset` con ese email
- **THEN** el sistema responde con código `200` con el mismo mensaje neutral
- **AND** no se genera ningún OTP ni se envía ningún mensaje

#### Scenario: Confirmación de reset exitosa con OTP válido

- **GIVEN** existe un OTP de tipo `PASSWORD_RESET` válido (no usado, no expirado) para el usuario
- **WHEN** se envía `POST /api/auth/password/confirmar-reset` con el `email`, el `otpCode` correcto y la nueva `password`
- **THEN** el sistema responde con código `200`
- **AND** la nueva contraseña se almacena como BCrypt cost 12
- **AND** el OTP queda marcado como `used=true`
- **AND** todos los refresh tokens del usuario se revocan

#### Scenario: Confirmación de reset fallida con OTP expirado (RN-AUTH-07)

- **GIVEN** existe un OTP de tipo `PASSWORD_RESET` para el usuario, pero `expires_at` es anterior al momento actual
- **WHEN** se envía `POST /api/auth/password/confirmar-reset` con ese OTP
- **THEN** el sistema responde con código `422`
- **AND** el mensaje indica que el código ha expirado sin revelar información adicional

---

## Casos límite

- Si el usuario no tiene `telegram_chat_id` vinculado y solicita reset de contraseña, el sistema responde `200` pero no puede enviar el OTP. El administrador debe gestionar el caso manualmente.
- El rate limiting de `POST /api/auth/login` (5/min/IP) se aplica antes de la validación de credenciales para no revelar si el login existe.
- Si un usuario en estado `INACTIVE` intenta hacer login, responde `403` con mensaje genérico.
- El `jti` en el JWT permite blacklisting puntual sin invalidar todos los tokens del usuario.
- El refresh token se almacena como `token_hash = SHA256(token)` en BD, nunca el token en claro.

## Dependencias con otras capabilities

- **`auth-otp-telegram`**: la recuperación de contraseña (`confirmar-reset`) consume el servicio OTP y requiere que la capability de vinculación Telegram esté operativa para enviar el código.
- **`usuarios`**: el registro crea un usuario en la tabla `users`; la aprobación de cuenta es responsabilidad de la capability `usuarios` (endpoint `PATCH /api/admin/usuarios/{id}/aprobar`).
- **`auditoria`**: cada evento de autenticación (login exitoso/fallido, logout, reset) genera una entrada en `audit_log`.
- **`roles-permisos`**: los endpoints admin requieren que la capa de seguridad valide el rol `ADMIN` antes de permitir el acceso.
