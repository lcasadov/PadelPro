# Capability: pagos-redsys

## Resumen
Gestión del ciclo de pago online mediante la pasarela Redsys (HMAC SHA-256) y del registro de pagos en efectivo por parte del ADMIN. Incluye la generación del formulario de pago firmado, el procesamiento del webhook de notificación de Redsys con validación de firma, la idempotencia ante webhooks duplicados, y la visualización del historial de pagos. Los datos de tarjeta (PAN, CVV, fecha de caducidad) nunca son almacenados ni registrados en logs.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-AUTH-04**: Un USER solo puede iniciar el pago de una reserva de la que es `owner_id`; los participantes no-owner no pueden iniciar el pago.
- **RN-RES-03**: El importe del pago se calcula exclusivamente en backend (`price_per_hour × duration_minutes / 60`); el importe enviado por el cliente es ignorado.
- **RN-PAY-01**: El webhook de Redsys valida la firma HMAC SHA-256 antes de procesar cualquier campo; una firma inválida resulta en rechazo silencioso (200 a Redsys) y registro en `audit_log`.
- **RN-PAY-02**: Idempotencia de pago: `redsys_order_id` es UNIQUE; un webhook duplicado para un pago ya en `status=PAID` se ignora sin reprocesar.
- **RN-PAY-03**: Los datos de tarjeta (PAN, CVV, fecha de caducidad, titular) nunca se almacenan ni aparecen en logs; PadelPro solo almacena `redsys_order_id` y `transaction_id` (Ds_AuthorisationCode).
- **RN-PAY-04**: Las transiciones de estado de pago son unidireccionales: `PENDING → IN_PROGRESS → PAID`; `PAID → REFUNDED`; `PENDING → CANCELLED`; `IN_PROGRESS → FAILED`. No se admite retroceso.

## Entidades implicadas

| Entidad | Tabla | Rol |
|---|---|---|
| Pago | `payments` | Entidad principal; creada atómicamente con la reserva en `status=PENDING` |
| Reserva | `reservations` | Referenciada por el pago; su estado puede actualizarse tras confirmación de pago |
| Usuario | `users` | El `owner_id` de la reserva inicia el pago; el admin registra pagos en efectivo |
| Configuración del sistema | `system_config` | Fuente de `redsys_merchant_code`, `redsys_terminal`, `redsys_secret_key` (cifrada AES-256) y `payment_gateway` |
| Log de auditoría | `audit_log` | Registra todos los eventos de pago: `PAYMENT_INITIATED`, `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `PAYMENT_WEBHOOK_INVALID_SIGNATURE`, `PAYMENT_CASH_REGISTERED` |

## Endpoints
*(de docs/openapi.yaml)*

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/pagos/iniciar` | Iniciar pago online Redsys (genera URL y parámetros firmados) |
| `GET` | `/api/pagos` | Listar pagos del usuario autenticado |
| `POST` | `/api/admin/pagos/{reservaId}/efectivo` | Registrar pago en efectivo (ADMIN) |
| `GET` | `/api/admin/pagos` | Listar todos los pagos (ADMIN) |
| Webhook | `POST /api/pagos/webhook` | Notificación de pago Redsys (server-to-server, sin JWT, validado por HMAC) |

## Permisos

| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Iniciar pago Redsys (`POST /api/pagos/iniciar`) | Permitido | Permitido (solo si owner) | Denegado (401) |
| Ver historial de pagos propios (`GET /api/pagos`) | Permitido (ve todos) | Permitido (solo propios) | Denegado (401) |
| Registrar pago en efectivo (`POST /api/admin/pagos/{reservaId}/efectivo`) | Permitido | Denegado (403) | Denegado (401) |
| Listar todos los pagos (`GET /api/admin/pagos`) | Permitido | Denegado (403) | Denegado (401) |
| Webhook Redsys (`POST /api/pagos/webhook`) | No aplica (validación HMAC) | No aplica | Validado por firma HMAC SHA-256 |

---

## Requirements

### Requirement 1: Iniciar pago online — generación de formulario Redsys firmado

**El sistema DEBE generar los parámetros de pago firmados con HMAC SHA-256 y devolver la URL del TPV virtual de Redsys cuando el owner de la reserva inicie el pago (RN-RES-03, RN-PAY-01).**

#### Scenario: USER owner inicia el pago de su reserva correctamente
- **GIVEN** el usuario autenticado "usuarioA" es `owner_id` de la reserva UUID-X con status `CONFIRMED`
- **AND** el pago asociado está en `status: PENDING`
- **AND** `system_config` tiene `redsys_merchant_code`, `redsys_terminal` y `redsys_secret_key` configurados
- **WHEN** "usuarioA" envía `POST /api/pagos/iniciar` con `reservaId: UUID-X` y `participanteId: <id-del-owner>`
- **THEN** el sistema responde 200 con `pagoId`, `redsysOrderId`, `redsysUrl`, `amount` y `status: "IN_PROGRESS"`
- **AND** el campo `amount` corresponde al importe calculado en backend (`price_per_hour × duration_minutes / 60`)
- **AND** el pago en BD pasa a `status: IN_PROGRESS`
- **AND** se registra `PAYMENT_INITIATED` en `audit_log`

#### Scenario: USER no-owner intenta iniciar el pago de una reserva ajena (RN-AUTH-04)
- **GIVEN** el usuario autenticado "usuarioB" es participante (no owner) de la reserva UUID-Y
- **WHEN** "usuarioB" envía `POST /api/pagos/iniciar` con `reservaId: UUID-Y` y `participanteId: <id-de-usuarioB>`
- **THEN** el sistema responde 403 con `code: "FORBIDDEN"`
- **AND** el pago no cambia de estado

#### Scenario: Intento de iniciar pago de reserva no existente
- **GIVEN** el UUID-INEXISTENTE no corresponde a ninguna reserva
- **WHEN** un usuario envía `POST /api/pagos/iniciar` con `reservaId: UUID-INEXISTENTE`
- **THEN** el sistema responde 404 con `code: "NOT_FOUND"`

#### Scenario: El importe del cliente es ignorado (RN-RES-03)
- **GIVEN** el pago tiene `amount=15.00` calculado en backend para una reserva de 60 min
- **WHEN** el cliente intenta pasar un importe diferente en el body (campo no previsto en el schema)
- **THEN** el sistema ignora cualquier campo de importe proveniente del cliente
- **AND** el parámetro `DS_MERCHANT_AMOUNT` enviado a Redsys corresponde siempre al importe calculado en backend (15.00 EUR = 1500 céntimos)

### Requirement 2: Procesar webhook Redsys con firma válida

**El sistema DEBE validar la firma HMAC SHA-256 del webhook de Redsys y, si es válida, actualizar el estado del pago a `PAID` y registrar el evento en `audit_log` (RN-PAY-01).**

#### Scenario: Webhook Redsys con firma válida — pago aprobado
- **GIVEN** existe un pago con `redsys_order_id: "ORDER-20250615-001"` en `status: IN_PROGRESS`
- **WHEN** Redsys envía `POST /api/pagos/webhook` con `Ds_SignatureVersion: "HMAC_SHA256_V1"`, `Ds_MerchantParameters` con `Ds_Response: "0000"` y `Ds_Signature` válida
- **THEN** el sistema responde 200 (siempre, para que Redsys no reintente)
- **AND** el pago pasa a `status: PAID` con `paid_at` registrado
- **AND** `transaction_id` se actualiza con `Ds_AuthorisationCode`
- **AND** se registra `PAYMENT_CONFIRMED` en `audit_log`

#### Scenario: Webhook Redsys con firma válida — pago rechazado
- **GIVEN** existe un pago con `redsys_order_id: "ORDER-20250615-002"` en `status: IN_PROGRESS`
- **WHEN** Redsys envía webhook con `Ds_Response: "0190"` (rechazo) y firma HMAC válida
- **THEN** el sistema responde 200
- **AND** el pago pasa a `status: FAILED`
- **AND** se registra `PAYMENT_REJECTED` en `audit_log`

### Requirement 3: Rechazo silencioso de webhook con firma inválida

**El sistema DEBE ignorar el webhook y registrar el intento cuando la firma HMAC SHA-256 no coincide, respondiendo siempre 200 a Redsys para evitar reintentos (RN-PAY-01).**

#### Scenario: Webhook con firma HMAC inválida
- **GIVEN** llega un `POST /api/pagos/webhook` con `Ds_Signature` que no corresponde a la clave configurada
- **WHEN** el sistema calcula la firma esperada y la compara con la recibida
- **THEN** el sistema responde 200 a Redsys (no revela el rechazo al posible atacante)
- **AND** el estado del pago en BD no cambia
- **AND** se registra `PAYMENT_WEBHOOK_INVALID_SIGNATURE` en `audit_log` con nivel CRÍTICO
- **AND** los datos de la petición no se procesan en ninguna lógica de negocio

### Requirement 4: Idempotencia ante webhooks duplicados

**El sistema DEBE ignorar un webhook duplicado sin reprocesar el pago cuando el `redsys_order_id` ya está en `status=PAID` (RN-PAY-02).**

#### Scenario: Webhook duplicado para un pago ya confirmado
- **GIVEN** el pago con `redsys_order_id: "ORDER-20250615-003"` está en `status: PAID`
- **WHEN** Redsys reenvía el mismo webhook con firma válida (reintento automático de Redsys)
- **THEN** el sistema responde 200
- **AND** el pago permanece en `status: PAID` sin modificación
- **AND** no se registra un nuevo `PAYMENT_CONFIRMED` en `audit_log` (o se registra con nota de duplicado)

#### Scenario: Segundo pago con redsys_order_id duplicado (RN-PAY-02)
- **GIVEN** ya existe un pago con `redsys_order_id: "ORDER-20250615-004"` en cualquier estado
- **WHEN** el sistema intenta crear un nuevo pago con el mismo `redsys_order_id`
- **THEN** la constraint UNIQUE de BD rechaza la operación
- **AND** el sistema devuelve 409 al cliente o maneja el conflicto internamente como idempotencia

### Requirement 5: Registro de pago en efectivo por ADMIN

**El sistema DEBE registrar el pago en efectivo de una reserva y actualizar el estado del pago a `PAID` cuando el ADMIN confirme la recepción del dinero.**

#### Scenario: ADMIN registra pago en efectivo de una reserva pendiente
- **GIVEN** la reserva UUID-C tiene un pago asociado en `status: PENDING`
- **AND** un usuario con rol ADMIN autenticado
- **WHEN** el ADMIN envía `POST /api/admin/pagos/UUID-C/efectivo`
- **THEN** el sistema responde 200 con `reservaId`, `pagosActualizados` y `totalCobrado`
- **AND** el pago pasa a `status: PAID`, `method: CASH`, `registered_by_id` apunta al ADMIN que registró el cobro
- **AND** se registra `PAYMENT_CASH_REGISTERED` en `audit_log`

#### Scenario: USER intenta registrar pago en efectivo (denegado)
- **GIVEN** un usuario con rol USER autenticado
- **WHEN** envía `POST /api/admin/pagos/UUID-D/efectivo`
- **THEN** el sistema responde 403 con `code: "FORBIDDEN"`

#### Scenario: ADMIN intenta registrar pago en efectivo de una reserva ya cobrada
- **GIVEN** la reserva UUID-E ya tiene su pago en `status: PAID`
- **WHEN** el ADMIN envía `POST /api/admin/pagos/UUID-E/efectivo`
- **THEN** el sistema responde 422 indicando que todos los pagos de la reserva ya están completados

## Casos límite
- Iniciar pago para una reserva en estado `CANCELLED`: el sistema responde 422 indicando que la reserva no admite pagos en ese estado (RN-PAY-04).
- Iniciar pago cuando el pago ya está en `status: IN_PROGRESS` o `PAID`: el sistema responde 409 (conflicto, ya existe un pago activo o completado).
- Webhook recibido para un `redsys_order_id` que no existe en BD: el sistema registra alerta en `audit_log` y responde 200 (no revelar inconsistencia a posible atacante).
- Datos de tarjeta (PAN, CVV, fecha): nunca llegan al backend de PadelPro (el formulario Redsys redirige directamente al TPV de Redsys); si por error llegaran en algún campo de petición, el sistema los ignora y nunca los persiste ni los registra en logs (RN-PAY-03).
- Comparación de firma HMAC: debe realizarse en tiempo constante para evitar ataques de timing; no usar comparación de strings estándar con cortocircuito.
- Timeout del webhook: si Redsys no recibe 200 en 30 segundos, reintenta; el sistema debe ser idempotente ante cualquier número de reintentos.
- Transición inversa (`PAID → PENDING`): denegada; se registra el intento en `audit_log` (RN-PAY-04).

## Dependencias con otras capabilities
- **reservas**: Cada pago está asociado a una reserva (FK UNIQUE); la reserva se crea atómicamente con el pago en `status=PENDING`. El estado de la reserva puede actualizarse tras la confirmación del pago (ej. `PENDING_CONFIRMATION → CONFIRMED`).
- **pistas**: La configuración del sistema (incluidas las credenciales Redsys) se gestiona desde la capability `pistas` via `system_config`.
