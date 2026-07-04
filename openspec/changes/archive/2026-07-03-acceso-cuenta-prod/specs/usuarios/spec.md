## ADDED Requirements

### Requirement: Panel de administración de usuarios en la interfaz web

La interfaz web SHALL ofrecer a los administradores un panel para gobernar el acceso de los usuarios: listar usuarios con filtro por estado, aprobar cuentas pendientes y activar/desactivar cuentas. El panel SHALL ser accesible solo para usuarios con rol ADMIN; la autorización efectiva la impone el backend (`/api/admin/**` requiere ROLE_ADMIN) y el frontend añade un guard por rol como defensa en profundidad.

#### Scenario: ADMIN accede al panel de usuarios
- **GIVEN** un usuario autenticado con rol ADMIN
- **WHEN** navega al panel de administración de usuarios
- **THEN** ve la lista de usuarios y puede filtrar por estado (p. ej. `PENDING`)

#### Scenario: USER no puede acceder al panel
- **GIVEN** un usuario autenticado con rol USER
- **WHEN** intenta acceder a la ruta del panel admin
- **THEN** el frontend impide el acceso (redirección) y el backend rechazaría cualquier operación admin con 403

#### Scenario: ADMIN aprueba una cuenta pendiente desde el panel
- **GIVEN** existe un usuario en estado `PENDING`
- **WHEN** el ADMIN pulsa aprobar en el panel
- **THEN** el usuario pasa a `ACTIVE` (vía `PATCH /api/admin/usuarios/{id}/aprobar`)
- **AND** ese usuario puede iniciar sesión a continuación

#### Scenario: ADMIN activa o desactiva una cuenta
- **WHEN** el ADMIN cambia el estado de una cuenta desde el panel
- **THEN** el cambio se refleja vía los endpoints admin existentes y el listado se actualiza

### Requirement: Restablecimiento de contraseña por el administrador

El sistema SHALL permitir a un administrador restablecer la contraseña de un usuario generando una contraseña temporal; la contraseña temporal SHALL devolverse en claro exactamente una vez en la respuesta para que el administrador la comunique, SHALL persistirse cifrada con BCrypt, SHALL marcar la cuenta con `must_change_password = true` y SHALL NOT aparecer en ningún log (RN-RGPD-04, RN-AUTH-07).

#### Scenario: ADMIN restablece la contraseña de un usuario
- **GIVEN** un usuario existente
- **WHEN** el ADMIN dispara el restablecimiento de contraseña desde el panel
- **THEN** el sistema genera una contraseña temporal, la persiste cifrada con BCrypt
- **AND** marca la cuenta con `must_change_password = true`
- **AND** la devuelve en claro una sola vez en la respuesta al ADMIN
- **AND** el usuario puede iniciar sesión con la contraseña temporal y se le exige cambiarla

#### Scenario: La contraseña temporal no se expone fuera de la respuesta única
- **WHEN** se genera una contraseña temporal
- **THEN** no aparece en logs ni se persiste en claro
- **AND** una consulta posterior del usuario no devuelve la contraseña

#### Scenario: USER no puede resetear contraseñas de otros
- **WHEN** un usuario con rol USER intenta el endpoint de restablecimiento
- **THEN** el sistema responde 403
