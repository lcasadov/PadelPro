## ADDED Requirements

### Requirement: Administrador inicial en un despliegue nuevo

El sistema SHALL garantizar que exista al menos un administrador `ACTIVE` tras un despliegue nuevo, creándolo de forma idempotente a partir de credenciales del entorno cuando no exista ningún administrador, con la contraseña cifrada en BCrypt (RN-AUTH-07).

#### Scenario: Primer arranque sin ningún administrador
- **GIVEN** una base de datos sin ningún usuario con rol ADMIN
- **AND** las variables `ADMIN_EMAIL` y `ADMIN_PASSWORD` están definidas en el entorno
- **WHEN** el backend arranca
- **THEN** se crea un usuario ADMIN con estado `ACTIVE` y la contraseña cifrada con BCrypt

#### Scenario: Arranque cuando ya existe un administrador
- **GIVEN** ya existe al menos un usuario con rol ADMIN
- **WHEN** el backend arranca de nuevo
- **THEN** no se crea ningún administrador adicional (operación idempotente)

#### Scenario: Arranque sin credenciales de admin configuradas
- **GIVEN** no existe ningún ADMIN y `ADMIN_EMAIL`/`ADMIN_PASSWORD` no están definidas
- **WHEN** el backend arranca
- **THEN** el arranque no falla y no se crea ningún administrador

### Requirement: Acceso provisional de 2 días para cuentas pendientes

El sistema SHALL permitir iniciar sesión a un usuario `PENDING` durante las 48 horas siguientes a su registro (calculadas sobre `created_at`). Pasado ese plazo sin aprobación, el login SHALL devolver `403 ACCOUNT_NOT_ACTIVE`. La aprobación del administrador deja la cuenta `ACTIVE` de forma permanente; una cuenta `INACTIVE` (desactivada por el admin) no tiene periodo de gracia y SHALL bloquearse siempre.

#### Scenario: PENDING dentro de la ventana de gracia
- **GIVEN** un usuario `PENDING` registrado hace menos de 48 horas
- **WHEN** inicia sesión con credenciales correctas
- **THEN** el sistema responde 200 y permite el uso provisional de la app

#### Scenario: PENDING fuera de la ventana de gracia
- **GIVEN** un usuario `PENDING` registrado hace más de 48 horas
- **WHEN** intenta iniciar sesión con credenciales correctas
- **THEN** el sistema responde 403 con `code: "ACCOUNT_NOT_ACTIVE"`

#### Scenario: Cuenta desactivada no tiene gracia
- **GIVEN** un usuario `INACTIVE`
- **WHEN** intenta iniciar sesión
- **THEN** el sistema responde 403 con `code: "ACCOUNT_NOT_ACTIVE"` independientemente de su antigüedad

### Requirement: Distinción entre cuenta no activa y credenciales inválidas en el login

El sistema SHALL diferenciar una cuenta no activa (403 `ACCOUNT_NOT_ACTIVE`) de credenciales inválidas (401), y la interfaz web SHALL mostrar mensajes distintos para cada caso.

#### Scenario: Login con credenciales incorrectas
- **WHEN** un usuario intenta iniciar sesión con un email inexistente o una contraseña incorrecta
- **THEN** el sistema responde 401
- **AND** la interfaz web muestra un mensaje de credenciales inválidas

#### Scenario: La UI distingue cuenta pendiente/bloqueada de credenciales
- **WHEN** el login devuelve `403 ACCOUNT_NOT_ACTIVE`
- **THEN** la interfaz web muestra un mensaje de cuenta pendiente/no activa, distinto del de credenciales inválidas

### Requirement: Confirmación tras el registro

La interfaz web SHALL mostrar, tras un registro correcto, una pantalla de confirmación que informe de que la cuenta queda pendiente de aprobación del administrador y de que dispone de acceso provisional durante 2 días.

#### Scenario: Pantalla posterior al registro
- **WHEN** un usuario completa el registro con éxito
- **THEN** ve una confirmación indicando que su cuenta está pendiente de aprobación y que tiene acceso provisional de 2 días

### Requirement: Cambio de contraseña forzado tras un restablecimiento

Cuando una cuenta tiene `must_change_password = true` (tras un reset del administrador), el sistema SHALL exigir el cambio de contraseña en el siguiente acceso antes de permitir el uso normal de la app, y SHALL limpiar el flag al cambiarla.

#### Scenario: Login con cambio de contraseña pendiente
- **GIVEN** una cuenta con `must_change_password = true`
- **WHEN** el usuario inicia sesión con la contraseña temporal
- **THEN** la interfaz web le exige establecer una nueva contraseña antes de continuar

#### Scenario: Tras cambiar la contraseña se limpia el flag
- **WHEN** el usuario establece una nueva contraseña válida
- **THEN** `must_change_password` pasa a `false` y puede usar la app con normalidad

### Requirement: Recuperación de contraseña gobernada por el administrador

La interfaz web SHALL ofrecer, desde el login, una vía para los usuarios que olvidan su contraseña que los dirija a contactar con el administrador del club; el restablecimiento efectivo lo realiza el administrador (ver capability `usuarios`). El sistema SHALL NOT exponer un reset self-service por email en esta fase.

#### Scenario: Usuario pulsa "¿Olvidaste la contraseña?"
- **WHEN** el usuario pulsa "¿Olvidaste la contraseña?" en la pantalla de login
- **THEN** la app navega a una pantalla que le indica contactar con el administrador del club para restablecer su acceso

#### Scenario: No existe endpoint público de reset por email
- **WHEN** se inspecciona la API pública de autenticación
- **THEN** no existe ningún endpoint de reset self-service por email/token en esta fase
