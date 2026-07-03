## ADDED Requirements

### Requirement: Alta de usuarios desde el panel de administración

La interfaz web SHALL permitir a un administrador dar de alta un usuario nuevo (nombre, email, rol) desde el panel de administración, quedando la cuenta `ACTIVE`. La contraseña SHALL generarla el sistema (no la teclea el administrador), marcarse `must_change_password = true` y comunicarse al usuario por email de bienvenida (ver capability `notificaciones`).

#### Scenario: ADMIN da de alta un usuario
- **GIVEN** un administrador autenticado en el panel
- **WHEN** completa el formulario de alta con nombre, email y rol y confirma
- **THEN** se crea un usuario `ACTIVE` con contraseña generada por el sistema y `must_change_password = true`
- **AND** el usuario aparece en la lista del panel
- **AND** se dispara el email de bienvenida con la contraseña

#### Scenario: Alta con email ya existente
- **WHEN** el administrador intenta dar de alta un usuario con un email ya registrado
- **THEN** el sistema responde con error de conflicto y la interfaz lo muestra sin crear duplicado

### Requirement: Edición de datos de contacto de usuario desde el panel de administración

La interfaz web SHALL permitir a un administrador editar los datos de contacto de un usuario existente (nombre, email, teléfono) desde el panel, vía `PATCH /api/admin/usuarios/{id}`. El formulario de edición SHALL NOT permitir cambiar el rol del usuario (para evitar escalada de privilegios).

#### Scenario: ADMIN edita los datos de contacto
- **GIVEN** un administrador autenticado en el panel
- **WHEN** abre la edición de un usuario, modifica nombre/email/teléfono y guarda
- **THEN** los cambios se persisten y la lista del panel refleja los nuevos datos

#### Scenario: El formulario de edición no expone el rol
- **WHEN** el administrador abre la edición de un usuario
- **THEN** el formulario no ofrece cambiar el rol (USER↔ADMIN)

#### Scenario: Edición con datos inválidos
- **WHEN** el administrador guarda una edición con datos inválidos (p. ej. email mal formado)
- **THEN** el sistema responde 400 y la interfaz muestra el error sin aplicar el cambio

### Requirement: Email de bienvenida al aprobar o activar

El sistema SHALL disparar el email de bienvenida (capability `notificaciones`) cuando una cuenta pasa a `ACTIVE`, tanto en el alta directa (email con contraseña generada) como en la aprobación de una cuenta pendiente (email sin contraseña; el usuario conserva la suya).

#### Scenario: Aprobación dispara el email sin resetear la contraseña
- **WHEN** el administrador aprueba una cuenta `PENDING` desde el panel
- **THEN** la cuenta pasa a `ACTIVE` conservando la contraseña que el usuario eligió al registrarse
- **AND** se envía el email de bienvenida sin contraseña
