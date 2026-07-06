## ADDED Requirements

### Requirement: Email de confirmación de reserva
El sistema SHALL enviar un email al titular cuando su reserva pase a `CONFIRMED`, con fecha, hora, duración e importe, y SHALL registrar el envío en `notification_log`. El envío es asíncrono y post-commit; un fallo NO revierte ni bloquea la confirmación. (La notificación Telegram de este evento queda diferida a `auth-otp-telegram`.)

#### Scenario: Reserva confirmada envía email al titular
- **WHEN** una reserva pasa a `CONFIRMED`
- **THEN** el sistema envía un email de confirmación al `email` del titular con los datos de la reserva y registra una entrada `type=EMAIL`, `status=SENT` en `notification_log`

#### Scenario: Fallo de envío no revierte la confirmación
- **WHEN** el SMTP no está disponible al confirmar una reserva
- **THEN** la reserva permanece `CONFIRMED` y se registra la notificación con `status=FAILED` y `error_message`, sin propagar excepción al flujo de negocio

### Requirement: Email de cancelación de reserva
El sistema SHALL enviar un email al titular cuando su reserva pase a `CANCELLED`, indicando el motivo si está disponible, y SHALL registrar el envío en `notification_log`. Solo se notifica al titular en v1 (no a participantes).

#### Scenario: Reserva cancelada notifica al titular
- **WHEN** una reserva pasa a `CANCELLED`
- **THEN** el sistema envía un email de cancelación al titular y registra la entrada en `notification_log`; no se notifica a los participantes no-titulares

### Requirement: Email de recibo de pago confirmado
El sistema SHALL enviar un email de recibo al titular de la reserva cuando su `Payment` pase a `PAID` (por webhook Redsys o por registro de efectivo del ADMIN), con importe, fecha y referencia, y SHALL registrar el envío en `notification_log`.

#### Scenario: Pago confirmado envía recibo
- **WHEN** un pago pasa a `PAID`
- **THEN** el sistema envía un recibo por email al titular con importe/fecha/referencia y registra la entrada en `notification_log`

### Requirement: Registro de notificaciones en notification_log
El sistema SHALL registrar cada intento de envío de notificación en `notification_log` con `status` (PENDING/SENT/FAILED), `type`, `recipient`, `subject`, `message`, `error_message`, `related_entity_type/id` y timestamps. El `message` y `recipient` NO SHALL contener contraseñas, tokens, OTP ni datos de tarjeta (RN-RGPD-04).

#### Scenario: Cada envío deja traza
- **WHEN** el sistema intenta enviar cualquier notificación
- **THEN** existe una entrada en `notification_log` con el resultado (`SENT` o `FAILED`)

#### Scenario: Sin datos sensibles en el registro
- **WHEN** se inspecciona `notification_log`
- **THEN** ningún `message`/`recipient` contiene contraseñas, tokens, OTP ni datos de tarjeta

### Requirement: Reintento automático de emails fallidos
El sistema SHALL reintentar automáticamente (job `@Scheduled`) los envíos de email en `status=FAILED` hasta un máximo de 3 intentos con backoff; tras el tercer intento fallido, la entrada permanece `FAILED` sin más reintentos.

#### Scenario: Email fallido se reintenta y acaba en SENT
- **WHEN** un email quedó `FAILED` por un fallo transitorio de SMTP y el SMTP vuelve a estar disponible
- **THEN** el job de reintentos lo reenvía en un ciclo posterior y la entrada pasa a `SENT`

#### Scenario: Tras 3 intentos fallidos no se reintenta más
- **WHEN** un email ha fallado 3 veces
- **THEN** la entrada permanece `FAILED` y el job no vuelve a intentarlo
