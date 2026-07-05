## Context

`payments` está creada (V9) con todo lo necesario para Redsys: `redsys_order_id` (UNIQUE, idempotencia RN-PAY-02), `payment_url`, `transaction_id` (Ds_AuthorisationCode), `gateway`, `method` (REDSYS/CASH), `status` (PENDING/IN_PROGRESS/PAID/FAILED/CANCELLED/REFUNDED), `paid_at`, `registered_by_id`. Cada reserva nace con un `Payment` en PENDING (importe congelado). `system_config` guarda `payment_gateway` (CASH/REDSYS), `redsys_merchant_id`, `redsys_merchant_key` cifrados AES-256; **falta `redsys_terminal`**. No hay endpoints de pago ni firma. El servicio de cifrado y el descifrado de secretos de `system_config` ya existen (usados por `configuracion-club`).

Protocolo Redsys (redirección): el backend envía al TPV un form con `Ds_SignatureVersion=HMAC_SHA256_V1`, `Ds_MerchantParameters` (Base64 del JSON de parámetros, incluido `Ds_Merchant_Order`, `Ds_Merchant_Amount` en céntimos, `Ds_Merchant_MerchantCode`, `Ds_Merchant_Terminal`, `Ds_Merchant_MerchantURL` = webhook, `Ds_Merchant_UrlOK/UrlKO`) y `Ds_Signature` = HMAC-SHA256 de `Ds_MerchantParameters` usando una clave por-pedido derivada (3DES del `Ds_Merchant_Order` con la clave secreta del comercio). El webhook recibe los mismos tres campos y se valida recomputando la firma.

## Goals / Non-Goals

**Goals:**
- Owner paga online el total de su reserva vía Redsys, con importe congelado en backend (RN-RES-03).
- Webhook robusto: firma válida en tiempo constante, idempotente, rechazo silencioso de firma inválida, nunca toca datos de tarjeta.
- Registro de pago en efectivo por ADMIN e historial de pagos.
- Testable sin credenciales reales del club (entorno sandbox Redsys).

**Non-Goals:**
- Pago compartido por participante (owner paga el total).
- Reembolso real en TPV, tokenización de tarjeta, panel de conciliación.

## Decisions

### D1 — Nuevo submódulo/paquete `pagos` dentro del backend
La lógica de pago (servicios, controllers, firma, DTOs) vive en su propio paquete (`com.padelpro.pagos` o `reservas.pagos`), reutilizando la entidad `Payment` y sus puertos. *Alternativa descartada:* meterlo en `reservas` — pago tiene su propio ciclo, seguridad (webhook sin JWT) y dependencias (cifrado, Redsys); separarlo mantiene la cohesión.

### D2 — Firma Redsys encapsulada en una utilidad probada por unit test
Una clase `RedsysSignature` implementa: derivar la clave por-pedido (3DES/CBC del `Ds_Merchant_Order` con la clave secreta Base64 del comercio) y calcular/verificar el HMAC-SHA256 de `Ds_MerchantParameters`. La comparación de firmas es en **tiempo constante** (`MessageDigest.isEqual`), RN-PAY-01 y caso límite de timing. Se testea con vectores del entorno de pruebas Redsys (clave sandbox pública). *Alternativa descartada:* librería de terceros — el algoritmo es corto y auditable; evitar dependencia con CVEs.

### D3 — Webhook: validar firma antes de tocar nada, idempotente, siempre 200
`ProcesarWebhookService`: (1) parsea `Ds_MerchantParameters` (Base64→JSON), (2) recomputa y compara la firma en tiempo constante; si no coincide → audita `PAYMENT_WEBHOOK_INVALID_SIGNATURE` (CRÍTICO) y responde **200** sin tocar el pago (RN-PAY-01); (3) localiza el pago por `redsys_order_id`; si ya está `PAID` → no reprocesa (RN-PAY-02) y responde 200; (4) según `Ds_Response` (`0000..0099` = OK) → `PAID` + `transaction_id` + `paid_at` + audita `PAYMENT_CONFIRMED`; si rechazo → `FAILED` + audita `PAYMENT_REJECTED`. Siempre 200 a Redsys para no provocar reintentos innecesarios. *Alternativa descartada:* responder 4xx en firma inválida — revelaría el rechazo a un atacante y provocaría reintentos.

### D4 — `redsys_terminal` como nueva columna cifrable en `system_config`
Migración Flyway pequeña que añade `redsys_terminal` (TEXT). Se lee/descifra con el mismo mecanismo que `merchant_id`/`merchant_key`. Default operativo "1" si no está configurado. *Alternativa considerada:* derivarlo del merchant_id — no es correcto; el terminal es un dato propio del comercio.

### D5 — Iniciar pago: transición atómica PENDING→IN_PROGRESS y guardas de estado
`IniciarPagoService`: valida owner (403), reserva existe (404) y no cancelada (422), pago en `PENDING` (si `IN_PROGRESS`/`PAID` → 409), genera `redsys_order_id` (formato Redsys: 4 dígitos numéricos + alfanumérico, único), calcula el form firmado, marca `IN_PROGRESS` y persiste `payment_url`. Importe = `PriceCalculator` de backend, en céntimos para Redsys (RN-RES-03). Auditoría `PAYMENT_INITIATED`.

### D6 — Frontend: auto-submit del form al TPV, retorno por UrlOK/UrlKO + verificación por webhook
La `CheckoutRedsysPage` construye un form oculto con los 3 campos y hace auto-submit al `redsysUrl` (redirección del navegador al TPV; los datos de tarjeta nunca tocan PadelPro). El **estado real del pago lo fija el webhook** (server-to-server), no el retorno del navegador; `PagoConfirmadoPage` (UrlOK/UrlKO) consulta el estado del pago del backend para mostrarlo (no confía en parámetros de la URL de retorno). *Alternativa descartada:* confiar en el retorno del navegador para marcar PAID — manipulable; el webhook es la fuente de verdad.

## Risks / Trade-offs

- [Sin credenciales reales del club no se puede probar end-to-end en prod] → Mitigación: usar el **entorno de pruebas Redsys** (comercio sandbox público) para dev/test; producción configura credenciales reales cifradas en `system_config`. Los unit tests de firma usan vectores conocidos.
- [El webhook llega sin JWT: superficie de ataque] → Mitigación: única protección válida = firma HMAC en tiempo constante; rechazo silencioso; sin efectos secundarios antes de validar; rate limiting del endpoint.
- [Datos de tarjeta llegan por error a un campo] → Mitigación: el backend solo lee los campos Redsys esperados; nunca persiste ni loguea el cuerpo crudo (RN-PAY-03); revisar que ningún logger vuelca `Ds_MerchantParameters` completo.
- [Retorno del navegador y webhook compiten (usuario vuelve antes que el webhook)] → Mitigación: `PagoConfirmadoPage` muestra "procesando" y refresca el estado; el webhook es idempotente y autoritativo.
- [Firma mal derivada (3DES) rompe todo silenciosamente] → Mitigación: unit tests de `RedsysSignature` con vectores del sandbox; test de round-trip (firmar→verificar).

## Migration Plan

- Una migración Flyway (siguiente Vn) añade `redsys_terminal` a `system_config` (nullable, cifrable). Sin otros cambios de esquema (`payments` ya está completa).
- Despliegue: backend + frontend aditivos. `payment_gateway` sigue en CASH por defecto; activar REDSYS por config cuando haya credenciales. Rollback = revertir; los pagos existentes (PENDING/CASH) no se ven afectados.

## Open Questions

Resueltas en revisión (2026-07-05):
- **Credenciales** → entorno de pruebas (sandbox) de Redsys para dev/test; reales en prod vía `system_config` cifrado.
- **`redsys_terminal`** → migración Flyway con columna nueva (cifrable, default "1").
- **Activación** → `payment_gateway` queda en CASH por defecto; REDSYS se activa por config cuando haya credenciales reales.
