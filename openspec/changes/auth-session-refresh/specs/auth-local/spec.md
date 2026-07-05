## ADDED Requirements

### Requirement: Renovación de access token vía refresh token
El sistema SHALL exponer `POST /api/auth/refresh` que, a partir de la cookie httpOnly `refresh_token`, emite un nuevo access token JWT (15 min) y rota el refresh token: valida que el refresh token exista (por hash), no esté expirado ni revocado, **revoca el usado** y emite uno nuevo con una nueva ventana de 7 días, devolviéndolo en una nueva cookie `refresh_token; HttpOnly; Secure; SameSite=Strict`. El endpoint NO SHALL requerir un access token válido.

#### Scenario: Refresh con cookie válida
- **WHEN** el cliente llama a `POST /api/auth/refresh` con una cookie `refresh_token` válida (no expirada, no revocada)
- **THEN** el sistema responde 200 con `{ access_token, token_type: "Bearer", expires_in: 900 }` y un `Set-Cookie` con un nuevo `refresh_token`, y el refresh token anterior queda revocado

#### Scenario: Refresh con cookie ausente
- **WHEN** el cliente llama a `POST /api/auth/refresh` sin cookie `refresh_token`
- **THEN** el sistema responde 401 y no emite ningún token

#### Scenario: Refresh con token expirado o revocado
- **WHEN** el cliente llama a `POST /api/auth/refresh` con un `refresh_token` expirado o ya revocado
- **THEN** el sistema responde 401 y no emite ningún token

#### Scenario: El refresh token usado no se puede reutilizar
- **WHEN** un `refresh_token` se usa con éxito y se intenta usar de nuevo el mismo valor
- **THEN** el segundo intento responde 401 (fue revocado en la rotación)

#### Scenario: Refresh no requiere access token
- **WHEN** el cliente llama a `POST /api/auth/refresh` sin cabecera `Authorization` pero con cookie válida
- **THEN** el sistema responde 200 y renueva la sesión

### Requirement: Renovación silenciosa de sesión en el cliente
El cliente web SHALL renovar la sesión de forma transparente: ante una respuesta **401** en una petición autenticada, SHALL intentar `POST /api/auth/refresh` una sola vez y, si tiene éxito, reintentar la petición original con el nuevo access token; si el refresh falla, SHALL limpiar la sesión y redirigir a la pantalla de login. Peticiones 401 concurrentes SHALL compartir una única renovación en vuelo.

#### Scenario: Renovación transparente ante 401
- **WHEN** una petición autenticada recibe 401 por access token caducado y el refresh token sigue vigente
- **THEN** el cliente renueva el token y reintenta la petición original, que se completa con éxito sin intervención del usuario

#### Scenario: Refresh fallido lleva a login
- **WHEN** una petición recibe 401 y el intento de refresh también falla (refresh token expirado/revocado)
- **THEN** el cliente limpia la sesión y redirige al usuario a la pantalla de login

#### Scenario: Múltiples 401 concurrentes disparan una sola renovación
- **WHEN** varias peticiones autenticadas reciben 401 casi simultáneamente
- **THEN** el cliente ejecuta un único `POST /api/auth/refresh` y, al resolverse, reintenta todas las peticiones pendientes con el nuevo token

#### Scenario: No hay bucle de refresh
- **WHEN** la propia petición `POST /api/auth/refresh` responde 401
- **THEN** el cliente no vuelve a intentar refrescar sobre esa respuesta y procede a redirigir a login
