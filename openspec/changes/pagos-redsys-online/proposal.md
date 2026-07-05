## Why

El pago online está definido (`pagos-redsys` spec + mockups 09/10) pero **sin implementar**: no existe ningún endpoint de pago (`/api/pagos/*`), ni firma HMAC, ni webhook. Hoy el cobro es solo presencial (CASH, confirmado por ADMIN de forma manual — y ese registro tampoco tiene endpoint). El botón "pagar ahora" que dejamos deshabilitado en `reservas-ui-jugador-fixes` sigue muerto. Es el candidato Wave 4A y cierra el bucle de pago diferido dos veces (reservas y partidas).

La base de datos ya está lista (`payments` con `redsys_order_id` UNIQUE, `payment_url`, `transaction_id`, `gateway`, `method`, `status` con todas las transiciones, `paid_at`, `registered_by_id`; `system_config` con `payment_gateway`, `redsys_merchant_id`, `redsys_merchant_key` cifrados). El gap es la lógica de aplicación + un pequeño ajuste de config (`redsys_terminal`).

## What Changes

- **[Backend] `POST /api/pagos/iniciar`** (solo owner, RN-AUTH-04): genera los parámetros del TPV Redsys firmados con **HMAC SHA-256** (importe congelado en backend RN-RES-03), pasa el pago a `IN_PROGRESS`, audita `PAYMENT_INITIATED`, y devuelve `redsysUrl` + parámetros firmados para redirigir al TPV. 403 si no es owner, 404 si la reserva no existe, 409 si ya hay pago activo/pagado, 422 si la reserva está cancelada.
- **[Backend] `POST /api/pagos/webhook`** (server-to-server, sin JWT): valida la firma HMAC en **tiempo constante** (RN-PAY-01); si es válida → `PAID`/`FAILED` según `Ds_Response`, registra `transaction_id`, audita; **idempotente** ante reintentos (RN-PAY-02); firma inválida → **rechazo silencioso** (200 a Redsys) + `PAYMENT_WEBHOOK_INVALID_SIGNATURE` en audit. Nunca almacena ni loguea datos de tarjeta (RN-PAY-03).
- **[Backend] `POST /api/admin/pagos/{reservaId}/efectivo`** (ADMIN): registra pago en efectivo → `PAID`, `method=CASH`, `registered_by_id`, audita `PAYMENT_CASH_REGISTERED`.
- **[Backend] `GET /api/pagos`** (propios) y **`GET /api/admin/pagos`** (todos): historial de pagos.
- **[Config] `redsys_terminal`** en `system_config` (migración Flyway pequeña; hoy hay `merchant_id`/`merchant_key` pero no terminal). Descifrado con el servicio AES-256 existente.
- **[Frontend] Checkout Redsys** (mockup 09): tras confirmar/elegir "pagar ahora", auto-envía el formulario firmado al TPV de Redsys (los datos de tarjeta NUNCA pasan por PadelPro, RN-PAY-03). **Pago confirmado** (mockup 10): pantalla de retorno con estado, código de reserva y referencia Redsys.
- **[Frontend] Activar "pagar ahora"** en Mis Reservas (hoy deshabilitado desde `reservas-ui-jugador-fixes`).

## Capabilities

### New Capabilities
<!-- Ninguna nueva: `pagos-redsys` ya existe como spec; este change la implementa. -->

### Modified Capabilities
- `pagos-redsys`: se implementan sus 5 requirements (iniciar pago firmado, webhook válido, rechazo de firma inválida, idempotencia, pago en efectivo ADMIN).
- `reservas`: se activa la acción "pagar ahora" en la UI y la reserva puede reflejar el estado de pago actualizado tras la confirmación (sin cambiar la lógica de dominio de creación/cancelación).

## Impact

- **Fase del producto**: fase-1 (`pagos-redsys` es fase-1, Wave 4A).
- **Backend** (`com.padelpro.reservas`/pagos): nuevos servicios `IniciarPagoService`, `ProcesarWebhookService`, `RegistrarPagoEfectivoService`, `PagoQueryService`; `PagoController` + `AdminPagoController` + webhook; utilidad de firma Redsys (HMAC SHA-256 sobre `Ds_MerchantParameters` con clave 3DES-derivada por pedido, según el protocolo Redsys); puertos de comando/consulta de `Payment`; eventos de auditoría. Alinear `docs/openapi.yaml` con lo implementado.
- **Frontend** (`frontend/src/`): `CheckoutRedsysPage` (auto-submit del form firmado), `PagoConfirmadoPage` (retorno); activar "pagar ahora" en `MisReservasPage`; funciones de pago en un service.
- **Config/Datos**: migración Flyway `redsys_terminal` en `system_config`. El resto del esquema de `payments` ya existe (sin migración). Credenciales: **entorno de pruebas Redsys (comercio sandbox)** para dev/test; producción vía `system_config` cifrado.
- **Seguridad**: firma HMAC en tiempo constante; datos de tarjeta fuera de PadelPro; webhook sin JWT protegido solo por firma; secretos descifrados en memoria (AES-256 existente). Auditoría de todos los eventos de pago.

## Fuera de alcance

- **Pago compartido por participante** ("tu parte" de partidas) — el spec de `pagos-redsys` es *owner paga el total* (RN-AUTH-04). El split 1:N (varios `Payment` por reserva / FK a participante) es una extensión posterior; aquí el owner paga el importe completo.
- **Reembolsos automáticos vía Redsys** más allá del marcado de estado `REFUNDED` existente al cancelar. La devolución real en el TPV es un flujo posterior.
- **Tokenización / tarjeta guardada** (el mockup muestra "tarjeta guardada" como preview) — no se almacena ni tokeniza tarjeta en v1.
- **Panel admin de conciliación de pagos** (mockup 23, "reservas del club") — capability `administracion-club`.
