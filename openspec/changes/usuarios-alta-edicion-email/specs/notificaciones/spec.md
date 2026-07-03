## ADDED Requirements

### Requirement: Envío de emails transaccionales por SMTP

El sistema SHALL poder enviar emails transaccionales a través de un proveedor SMTP configurable por entorno (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`). Las credenciales SHALL NOT estar en el repositorio.

#### Scenario: Configuración SMTP presente
- **GIVEN** las variables `MAIL_*` están definidas en el entorno
- **WHEN** el sistema necesita enviar un email
- **THEN** lo envía a través del servidor SMTP configurado usando el remitente `MAIL_FROM`

#### Scenario: Credenciales fuera del control de versiones
- **WHEN** se inspecciona el repositorio
- **THEN** no hay credenciales SMTP reales commiteadas (solo placeholders en `.env.example`)

### Requirement: Email de bienvenida al activar una cuenta

El sistema SHALL enviar un email de bienvenida al usuario cuando su cuenta se activa. El contenido depende del flujo: en el **alta directa** el email incluye la contraseña temporal generada por el sistema; en la **aprobación de una cuenta pendiente** el email da la bienvenida SIN incluir contraseña (el usuario conserva la que eligió al registrarse). El envío SHALL ser asíncrono y SHALL NOT bloquear ni revertir la activación si falla; ninguna contraseña SHALL registrarse en logs.

#### Scenario: Bienvenida tras el alta directa (con contraseña)
- **WHEN** el administrador da de alta un usuario que queda `ACTIVE`
- **THEN** el usuario recibe un email de bienvenida con la contraseña temporal generada por el sistema

#### Scenario: Bienvenida tras aprobar una cuenta pendiente (sin contraseña)
- **WHEN** el administrador aprueba una cuenta `PENDING`
- **THEN** el usuario recibe un email de bienvenida indicándole que su cuenta está aprobada
- **AND** el email NO incluye ninguna contraseña (el usuario usa la que eligió al registrarse)

#### Scenario: Un fallo de envío no rompe la activación
- **GIVEN** el servidor SMTP no está disponible
- **WHEN** el administrador activa una cuenta
- **THEN** la cuenta queda activada correctamente
- **AND** el fallo del email se registra sin exponer ninguna contraseña, sin propagar error al administrador
