## MODIFIED Requirements

### Requirement: Registro automático de eventos sensibles de autenticación

**El sistema DEBE registrar en `audit_log` todos los eventos de autenticación: login exitoso, login fallido y bloqueo de cuenta, con timestamp, IP de origen, login del usuario, canal y contexto de entidad.**

#### Scenario: Login fallido genera entrada `USER_LOGIN_FAILED`

- **WHEN** se realiza `POST /api/auth/login` con credenciales incorrectas
- **THEN** se inserta una fila en `audit_log` con `action='USER_LOGIN_FAILED'`, `entity_type='USER'`, `ip_address` = IP del cliente, `channel='WEB'`, `created_at` = instante actual
- **AND** `details` no contiene la contraseña introducida

#### Scenario: Login exitoso genera `USER_LOGIN_SUCCESS`

- **WHEN** se realiza `POST /api/auth/login` con credenciales correctas
- **THEN** se inserta una fila en `audit_log` con `action='USER_LOGIN_SUCCESS'`, `user_id` = id del usuario, `channel='WEB'`
- **AND** el campo `details` no contiene el access token ni el refresh token generados

---

### Requirement: Retención mínima de 2 años y consulta por ADMIN

**El sistema DEBE preservar todas las entradas de `audit_log` durante un mínimo de 2 años (RN-RGPD-02) y exponer un endpoint `GET /api/admin/audit` que permita al ADMIN consultarlas con filtros y paginación.**

#### Scenario: ADMIN consulta audit_log sin filtros

- **WHEN** un ADMIN autenticado envía `GET /api/admin/audit`
- **THEN** el sistema responde `200` con un `PagedAuditLogResponse` que contiene las entradas más recientes ordenadas por `createdAt` descendente
- **AND** la respuesta incluye los campos: `id`, `action`, `userId`, `userEmail`, `ipAddress`, `details`, `entityType`, `entityId`, `channel`, `createdAt`
- **AND** la paginación por defecto es `page=0, size=20`

#### Scenario: ADMIN filtra audit_log por acción

- **WHEN** un ADMIN autenticado envía `GET /api/admin/audit?action=ACCESS_DENIED`
- **THEN** el sistema responde `200` con entradas cuyo `action` es exactamente `ACCESS_DENIED`
- **AND** no se incluyen entradas con otras acciones

#### Scenario: ADMIN filtra audit_log por userId

- **WHEN** un ADMIN autenticado envía `GET /api/admin/audit?userId=42`
- **THEN** el sistema responde `200` con solo las entradas del usuario con `id=42`

#### Scenario: ADMIN filtra audit_log por rango de fechas

- **WHEN** un ADMIN autenticado envía `GET /api/admin/audit?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z`
- **THEN** el sistema responde `200` con entradas cuyo `createdAt` está dentro del rango indicado (ambos extremos inclusivos)

#### Scenario: ADMIN solicita página con size mayor a 100

- **WHEN** un ADMIN autenticado envía `GET /api/admin/audit?size=500`
- **THEN** el sistema responde `400` con cuerpo `{ "error": "VALIDATION_ERROR", "message": "size must be between 1 and 100" }`

#### Scenario: USER no puede acceder a audit_log

- **WHEN** un usuario autenticado con `role=USER` envía `GET /api/admin/audit`
- **THEN** el sistema responde `403` con cuerpo `{ "error": "ACCESS_DENIED", "message": "Insufficient permissions" }`

#### Scenario: Request no autenticado recibe 401

- **WHEN** se envía `GET /api/admin/audit` sin cabecera `Authorization`
- **THEN** el sistema responde `401` con cuerpo `{ "error": "AUTH_REQUIRED", "message": "Authentication required" }`

#### Scenario: Job de purga no elimina registros con menos de 2 años

- **WHEN** se ejecuta cualquier proceso de mantenimiento
- **THEN** solo se eliminan (o archivan) filas con `created_at < NOW() - INTERVAL '2 years'`
- **AND** el número de filas eliminadas queda registrado en el log de aplicación a nivel INFO sin incluir el contenido de las filas

---

### Requirement: Los logs de auditoría no contienen datos sensibles

**El sistema DEBE garantizar que ningún campo de `audit_log` contiene contraseñas, tokens JWT, códigos OTP en claro, claves de API ni datos de tarjeta.**

#### Scenario: Cambio de contraseña — `audit_log` no contiene la nueva contraseña

- **WHEN** un usuario realiza `PATCH /api/usuarios/me` con un nuevo valor de `password`
- **THEN** se inserta una fila en `audit_log` con `action='PASSWORD_CHANGED'`
- **AND** el campo `details` contiene únicamente metadatos (ej. `{"updatedFields":["password"]}`) sin el hash BCrypt ni la contraseña en claro

#### Scenario: `audit_log` rechaza inserción con datos sensibles (contrato de arquitectura)

- **WHEN** se compila el proyecto con ArchUnit activo
- **THEN** ninguna clase que construya un `AuditLog` puede pasar campos que contengan `password`, `token`, `otp` o `secret` en el campo `details` — la regla de naming convention enforced por revisión de código y tests de integración que verifican ausencia de esos términos en filas reales
