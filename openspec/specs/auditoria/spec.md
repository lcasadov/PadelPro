# Capability: `auditoria`

## Resumen
Registro inmutable de todos los eventos sensibles del sistema para garantizar trazabilidad
de seguridad, cumplimiento RGPD y soporte a la respuesta a incidentes. Las entradas en
`audit_log` son generadas automáticamente por las capabilities de aplicación; el ADMIN puede
leerlas a través de `GET /api/admin/audit` (Fase 2). En v1.0 el acceso es solo a través de
consulta directa a BD por el administrador del servidor.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-RGPD-02**: Los registros de `audit_log` deben conservarse un mínimo de 2 años; nunca se borran filas individualmente.
- **RN-RGPD-04**: Los campos `details` y demás columnas de `audit_log` nunca contienen contraseñas, tokens JWT, códigos OTP en claro ni datos de tarjeta.
- **RN-AUD-01**: Toda acción sensible (autenticación, gestión de usuarios, reservas, pagos, configuración, webhooks) genera una entrada en `audit_log` de forma síncrona dentro de la misma transacción de negocio cuando sea posible, o inmediatamente después si la acción es asíncrona (webhook).
- **RN-AUD-02**: Las entradas de `audit_log` son inmutables: no existe ningún endpoint ni proceso que permita modificarlas o eliminarlas.
- **RN-AUD-03**: El campo `user_id` puede ser NULL para acciones del sistema (webhooks Redsys, job de recordatorios) o cuando el usuario ha sido anonimizado (la FK es SET NULL).
- **RN-SEC-01**: Los fallos de autenticación (login fallido, OTP fallido, webhook con firma inválida) se registran con prioridad ALTA en `audit_log`.

## Entidades implicadas
- **`audit_log`**: tabla inmutable con una fila por evento auditado.
  - `id` (BIGSERIAL PK), `user_id` (FK nullable → `users` SET NULL), `action` (VARCHAR 100), `entity_type` (VARCHAR 50), `entity_id` (VARCHAR 36, UUID o BIGINT como texto), `details` (TEXT JSON sin datos sensibles), `ip_address` (VARCHAR 45), `channel` (WEB / TELEGRAM / SYSTEM), `created_at` (TIMESTAMPTZ inmutable).
- **`users`**: origen del `user_id` referenciado; en anonimización SET NULL preserva la traza histórica.

## Endpoints
Sin endpoint REST directo en v1.0 — las entradas se generan internamente.

> En v1.0 no existe endpoint `GET /api/admin/audit`. El ADMIN accede al log directamente
> en la base de datos PostgreSQL. En Fase 2 se expondrá como `GET /api/admin/audit` con
> filtros por `action`, `user_id`, `entity_type` y rango de fechas, con paginación obligatoria.

## Permisos
| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Generar entrada (interno) | N/A — sistema | N/A — sistema | N/A — sistema |
| Leer `audit_log` propio (Fase 2) | ✅ | ❌ | ❌ |
| Leer `audit_log` de cualquier usuario (Fase 2) | ✅ | ❌ | ❌ |
| Modificar o eliminar entradas | ❌ | ❌ | ❌ |

## Requirements

### Requirement 1: Registro automático de eventos sensibles de autenticación
**El sistema DEBE registrar en `audit_log` todos los eventos de autenticación: login exitoso, login fallido y bloqueo de cuenta, con timestamp, IP de origen, login del usuario y canal.**

#### Scenario 1: Login fallido genera entrada `USER_LOGIN_FAILED`
- **GIVEN** un usuario registrado con `status=ACTIVE`
- **WHEN** se realiza `POST /api/auth/login` con credenciales incorrectas
- **THEN** se inserta una fila en `audit_log` con `action='USER_LOGIN_FAILED'`, `entity_type='USER'`, `entity_id` = id del usuario (si el login existe) o NULL, `ip_address` = IP del cliente, `channel='WEB'`, `created_at` = instante actual, Y `details` no contiene la contraseña introducida

#### Scenario 2: Décimo fallo consecutivo de login genera `USER_ACCOUNT_LOCKED`
- **GIVEN** un usuario con 9 intentos fallidos previos dentro de la ventana de 10 minutos
- **WHEN** se realiza el décimo intento fallido de login
- **THEN** se insertan dos filas en `audit_log`: una con `action='USER_LOGIN_FAILED'` y otra con `action='USER_ACCOUNT_LOCKED'`, Y el `details` de `USER_ACCOUNT_LOCKED` incluye la duración del bloqueo (15 min) sin datos sensibles

#### Scenario 3: Login exitoso genera `USER_LOGIN_SUCCESS`
- **GIVEN** un usuario con `status=ACTIVE` y credenciales correctas
- **WHEN** se realiza `POST /api/auth/login` con éxito
- **THEN** se inserta una fila en `audit_log` con `action='USER_LOGIN_SUCCESS'`, `user_id` = id del usuario, `ip_address` = IP del cliente, Y el `details` no contiene el access token ni el refresh token generados

### Requirement 2: Registro de eventos de reservas y pagos
**El sistema DEBE registrar en `audit_log` la creación, cancelación y cambio de estado de reservas, así como la confirmación e invalidación de pagos.**

#### Scenario 4: Creación de reserva genera `RESERVATION_CREATED`
- **GIVEN** un usuario autenticado con `status=ACTIVE`
- **WHEN** `POST /api/reservas` crea una reserva correctamente
- **THEN** se inserta una fila en `audit_log` con `action='RESERVATION_CREATED'`, `entity_type='RESERVATION'`, `entity_id` = UUID de la reserva, `user_id` = id del titular, `channel` según el canal de creación (WEB o TELEGRAM)

#### Scenario 5: Webhook Redsys con firma inválida genera `PAYMENT_WEBHOOK_INVALID_SIGNATURE`
- **GIVEN** el backend recibe `POST /api/pagos/webhook` con un `Ds_Signature` que no supera la validación HMAC SHA-256
- **WHEN** `PagoWebhookAdapter` rechaza el webhook
- **THEN** se inserta una fila en `audit_log` con `action='PAYMENT_WEBHOOK_INVALID_SIGNATURE'`, `entity_type='PAYMENT'`, `channel='SYSTEM'`, `ip_address` = IP del remitente, Y `details` incluye el `Ds_MerchantParameters` truncado sin la firma completa ni datos de tarjeta

### Requirement 3: Registro de eventos de seguridad en webhooks
**El sistema DEBE registrar en `audit_log` cualquier intento de llamada al webhook de Telegram con un `X-Telegram-Bot-Api-Secret-Token` inválido.**

#### Scenario 6: Webhook Telegram con secret inválido genera `TELEGRAM_WEBHOOK_INVALID_SECRET`
- **GIVEN** el backend recibe `POST /api/bot/telegram` con un header `X-Telegram-Bot-Api-Secret-Token` que no coincide con el secret configurado
- **WHEN** `BotTelegramAdapter` rechaza el request con 403
- **THEN** se inserta una fila en `audit_log` con `action='TELEGRAM_WEBHOOK_INVALID_SECRET'`, `channel='SYSTEM'`, `ip_address` = IP del remitente, Y el secret inválido nunca aparece en `details` ni en ningún campo de la fila

### Requirement 4: Los logs de auditoría no contienen datos sensibles
**El sistema DEBE garantizar que ningún campo de `audit_log` contiene contraseñas, tokens JWT, códigos OTP en claro, claves de API ni datos de tarjeta.**

#### Scenario 7: Cambio de contraseña — `audit_log` no contiene la nueva contraseña
- **GIVEN** un usuario que realiza `PATCH /api/usuarios/me` con un nuevo valor de `password`
- **WHEN** `UsuarioApplicationService` procesa el cambio
- **THEN** se inserta una fila en `audit_log` con `action='PASSWORD_CHANGED'`, Y el campo `details` contiene únicamente metadatos (ej. `{"updatedFields":["password"]}`) sin el hash BCrypt ni la contraseña en claro

#### Scenario 8: ADMIN no puede leer `audit_log` de otro usuario vía endpoint USER
- **GIVEN** dos usuarios A y B con rol USER, ambos autenticados
- **WHEN** el usuario A intenta acceder a los registros de auditoría de B (en Fase 2 con endpoint hipotético `GET /api/audit?userId={B}`)
- **THEN** el sistema devuelve 403 Forbidden, Y no se devuelve ningún dato de `audit_log` de B al usuario A

### Requirement 5: Anonimización de usuario genera entrada de auditoría
**El sistema DEBE registrar en `audit_log` la anonimización de un usuario (RGPD Art. 17) con `action='USER_ANONYMIZED'` sin incluir los datos previos del usuario en `details`.**

#### Scenario 9: Anonimización genera `USER_ANONYMIZED`
- **GIVEN** un ADMIN que ejecuta `DELETE /api/admin/usuarios/{id}` para anonimizar una cuenta
- **WHEN** `UsuarioApplicationService` completa el proceso de anonimización
- **THEN** se inserta una fila en `audit_log` con `action='USER_ANONYMIZED'`, `entity_type='USER'`, `entity_id` = id del usuario anonimizado, `user_id` = id del ADMIN que ejecutó la acción, Y `details` no contiene el nombre, email, teléfono ni `telegram_chat_id` anteriores del usuario

### Requirement 6: Retención mínima de 2 años y consulta por ADMIN
**El sistema DEBE preservar todas las entradas de `audit_log` durante un mínimo de 2 años (RN-RGPD-02). Ningún proceso automático debe eliminar filas antes de ese plazo.**

#### Scenario 10: Job de purga no elimina registros con menos de 2 años
- **GIVEN** el job nocturno de mantenimiento de `audit_log` se ejecuta
- **WHEN** el job evalúa las filas a eliminar
- **THEN** solo se eliminan (o archivan) filas con `created_at < NOW() - INTERVAL '2 years'`, Y el número de filas eliminadas queda registrado en el log de aplicación a nivel INFO sin incluir el contenido de las filas

## Casos límite
- Si la transacción de negocio principal hace rollback (ej. reserva no creada por solapamiento), la entrada de `audit_log` asociada también se revierte (están en la misma transacción); el sistema no genera entradas de auditoría huérfanas de operaciones fallidas.
- En webhooks (Redsys, Telegram), el registro en `audit_log` es siempre la primera operación y no depende del éxito del procesamiento posterior.
- Si `user_id` está referenciado y el usuario es anonimizado posteriormente, la FK pasa a NULL (SET NULL); la entrada preexistente en `audit_log` conserva todos los demás campos para mantener la trazabilidad histórica.
- El campo `ip_address` puede ser NULL si la acción proviene de un job del sistema (`channel='SYSTEM'`).
- `audit_log` no tiene `UPDATE` ni `DELETE` permitidos a nivel de aplicación; solo `INSERT` y `SELECT`.
- La tabla de `audit_log` debe disponer de particionamiento por año (recomendado) cuando supere 500.000 filas, para mantener el rendimiento de las consultas de ADMIN sin degradar los `INSERT` de producción.

## Dependencias con otras capabilities
- **`autenticacion`**: genera eventos USER_LOGIN_SUCCESS, USER_LOGIN_FAILED, USER_ACCOUNT_LOCKED, OTP_GENERATED, OTP_VALIDATED, OTP_FAILED, PASSWORD_CHANGED, PASSWORD_RESET, USER_REGISTERED, USER_APPROVED, TELEGRAM_LINKED, TELEGRAM_UNLINKED.
- **`reservas`**: genera eventos RESERVATION_CREATED, RESERVATION_CANCELLED, RESERVATION_JOINED, RESERVATION_STATUS_CHANGED.
- **`pagos-redsys`**: genera eventos PAYMENT_INITIATED, PAYMENT_CONFIRMED, PAYMENT_REJECTED, PAYMENT_CASH_REGISTERED, PAYMENT_WEBHOOK_INVALID_SIGNATURE.
- **`exportaciones-rgpd`**: genera eventos USER_ANONYMIZED.
- **`notificaciones`**: el fallo de webhook Telegram (secret inválido) genera TELEGRAM_WEBHOOK_INVALID_SECRET a través del adaptador de entrada del bot.
- **`administracion-club`** (Fase 2): leerá `audit_log` para mostrar historial de actividad administrativa.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 20 | Confirmar eliminación | Mobile | USER | [`24-eliminar-cuenta.html`](../../../docs/ux/mockups/24-eliminar-cuenta.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo RGPD — derecho al olvido** — la anonimización de cuenta (pantalla 20) genera la entrada `USER_ANONYMIZED` en `audit_log`; esta entrada es el rastro inmutable que acredita el cumplimiento del Art. 17 RGPD.
