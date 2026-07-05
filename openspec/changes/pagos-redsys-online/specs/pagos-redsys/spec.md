## ADDED Requirements

### Requirement: Iniciar pago online con formulario Redsys firmado
El sistema SHALL exponer `POST /api/pagos/iniciar` que, solo para el owner de la reserva (RN-AUTH-04), genera los parámetros del TPV Redsys firmados con HMAC SHA-256 (importe congelado en backend, RN-RES-03), pasa el pago a `IN_PROGRESS`, audita `PAYMENT_INITIATED` y devuelve `redsysUrl` + parámetros para redirigir al TPV.

#### Scenario: Owner inicia el pago de su reserva
- **WHEN** el owner de una reserva con pago `PENDING` envía `POST /api/pagos/iniciar` con su `reservaId`
- **THEN** el sistema responde 200 con `{pagoId, redsysOrderId, redsysUrl, amount, status:"IN_PROGRESS"}`, el `amount` es el calculado en backend, el pago pasa a `IN_PROGRESS` y se audita `PAYMENT_INITIATED`

#### Scenario: No-owner no puede iniciar el pago
- **WHEN** un participante no-owner intenta iniciar el pago de la reserva
- **THEN** el sistema responde 403 y el pago no cambia de estado

#### Scenario: Reserva inexistente o no pagable
- **WHEN** se inicia el pago de una reserva inexistente / cancelada / con pago ya `IN_PROGRESS` o `PAID`
- **THEN** el sistema responde 404 / 422 / 409 respectivamente, sin generar una transacción nueva

#### Scenario: El importe del cliente se ignora
- **WHEN** el cliente incluye un importe en el cuerpo
- **THEN** el `Ds_Merchant_Amount` enviado a Redsys corresponde siempre al importe calculado en backend (en céntimos)

### Requirement: Procesar webhook Redsys con firma válida
El sistema SHALL exponer `POST /api/pagos/webhook` (sin JWT) que valida la firma HMAC SHA-256 en tiempo constante y, si es válida, actualiza el pago a `PAID` (o `FAILED` según `Ds_Response`), registra `transaction_id` y audita el evento. Responde siempre 200 a Redsys.

#### Scenario: Webhook válido con pago aprobado
- **WHEN** Redsys notifica con firma válida y `Ds_Response` de aprobación para un pago en `IN_PROGRESS`
- **THEN** el sistema responde 200, el pago pasa a `PAID` con `paid_at` y `transaction_id`, y audita `PAYMENT_CONFIRMED`

#### Scenario: Webhook válido con pago rechazado
- **WHEN** Redsys notifica con firma válida y `Ds_Response` de rechazo
- **THEN** el sistema responde 200, el pago pasa a `FAILED` y audita `PAYMENT_REJECTED`

### Requirement: Rechazo silencioso de webhook con firma inválida
El sistema SHALL responder 200 a Redsys, NO alterar el estado del pago y auditar `PAYMENT_WEBHOOK_INVALID_SIGNATURE` cuando la firma HMAC no coincide, sin procesar ningún campo del cuerpo como lógica de negocio.

#### Scenario: Firma inválida
- **WHEN** llega un webhook cuya `Ds_Signature` no corresponde a la clave configurada
- **THEN** el sistema responde 200, el pago no cambia y se audita `PAYMENT_WEBHOOK_INVALID_SIGNATURE`

### Requirement: Idempotencia ante webhooks duplicados
El sistema SHALL ignorar un webhook cuyo pago ya está en `PAID` sin reprocesarlo, y garantizar unicidad por `redsys_order_id` (RN-PAY-02).

#### Scenario: Reintento de Redsys sobre un pago ya confirmado
- **WHEN** Redsys reenvía el mismo webhook (firma válida) para un pago ya `PAID`
- **THEN** el sistema responde 200 y el pago permanece `PAID` sin nueva confirmación duplicada

### Requirement: Registro de pago en efectivo por ADMIN
El sistema SHALL exponer `POST /api/admin/pagos/{reservaId}/efectivo` (solo ADMIN) que marca el pago como `PAID`, `method=CASH`, `registered_by_id` = ADMIN, y audita `PAYMENT_CASH_REGISTERED`.

#### Scenario: ADMIN registra efectivo
- **WHEN** el ADMIN registra el pago en efectivo de una reserva con pago `PENDING`
- **THEN** el pago pasa a `PAID`/`CASH` con `registered_by_id` del ADMIN y se audita `PAYMENT_CASH_REGISTERED`

#### Scenario: USER no puede registrar efectivo
- **WHEN** un USER llama al endpoint de efectivo
- **THEN** el sistema responde 403

#### Scenario: Reserva ya cobrada
- **WHEN** el ADMIN registra efectivo de una reserva cuyo pago ya está `PAID`
- **THEN** el sistema responde 422 indicando que ya está cobrada

### Requirement: Los datos de tarjeta nunca se almacenan ni registran
El sistema NO SHALL almacenar ni registrar en logs datos de tarjeta (PAN, CVV, caducidad, titular). Solo persiste `redsys_order_id` y `transaction_id`. La comparación de firma se realiza en tiempo constante.

#### Scenario: Ningún dato de tarjeta persiste ni se loguea
- **WHEN** se procesa cualquier operación de pago
- **THEN** no se almacena ni aparece en logs ningún dato de tarjeta; solo `redsys_order_id`/`transaction_id`
