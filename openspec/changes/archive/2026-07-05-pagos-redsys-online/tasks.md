# Pago online Redsys (fase-1, Wave 4A)

> Orden TDD estricto (Red → Green → Refactor) en cada grupo. Owner paga el total (pago compartido por participante DIFERIDO). Tests contra el entorno de pruebas Redsys (comercio sandbox); datos de tarjeta NUNCA en PadelPro (RN-PAY-03).

## 1. Config — `redsys_terminal` + descifrado (D4)

- [x] 1.1 **[Red]** Test: `SystemConfig` expone `redsysTerminal` descifrado; default "1" si ausente
- [x] 1.2 **[Green]** Migración Flyway `Vn__add_redsys_terminal_to_system_config.sql` (TEXT, nullable, cifrable) + campo en la entidad/DTO + descifrado con el servicio AES-256 existente

## 2. Backend — firma Redsys (D2)

- [x] 2.1 **[Red]** Tests de `RedsysSignature` con vectores del sandbox: derivar clave por-pedido (3DES del `Ds_Merchant_Order`), calcular HMAC-SHA256 de `Ds_MerchantParameters`, round-trip firmar→verificar, comparación en tiempo constante (`MessageDigest.isEqual`)
- [x] 2.2 **[Green]** Utilidad `RedsysSignature` (firmar/verificar); sin dependencia de terceros; nunca loguea la clave

## 3. Backend — iniciar pago (D5)

- [x] 3.1 **[Red]** Tests `IniciarPagoService`: owner OK → IN_PROGRESS + form firmado + `PAYMENT_INITIATED`; no-owner → 403; reserva inexistente → 404; cancelada → 422; pago ya IN_PROGRESS/PAID → 409; importe del cliente ignorado (amount = backend en céntimos)
- [x] 3.2 **[Green]** `IniciarPagoService` + `PagoController` `POST /api/pagos/iniciar` + DTOs (request `reservaId`, response `pagoId/redsysOrderId/redsysUrl/amount/status`); genera `redsys_order_id` único; persiste `payment_url`; auditoría; documentar en openapi

## 4. Backend — webhook (D3)

- [x] 4.1 **[Red]** Tests `ProcesarWebhookService`: firma válida + aprobado → PAID + transaction_id + `PAYMENT_CONFIRMED`; firma válida + rechazo → FAILED + `PAYMENT_REJECTED`; firma inválida → 200 sin cambio + `PAYMENT_WEBHOOK_INVALID_SIGNATURE`; duplicado sobre PAID → 200 sin reproceso; order_id inexistente → 200 + alerta
- [x] 4.2 **[Green]** `ProcesarWebhookService` + `POST /api/pagos/webhook` (sin JWT, allowlist en SecurityConfig, rate limit); valida firma ANTES de tocar nada; idempotente; siempre 200; nunca persiste/loguea datos de tarjeta; documentar en openapi

## 5. Backend — efectivo ADMIN + historial

- [x] 5.1 **[Red]** Tests: ADMIN registra efectivo → PAID/CASH/registered_by + `PAYMENT_CASH_REGISTERED`; USER → 403; reserva ya PAID → 422. `GET /api/pagos` (propios) y `GET /api/admin/pagos` (todos)
- [x] 5.2 **[Green]** `RegistrarPagoEfectivoService` + `AdminPagoController` (`POST /api/admin/pagos/{reservaId}/efectivo`, `GET /api/admin/pagos`); `PagoQueryService` + `GET /api/pagos`; documentar en openapi

## 6. Frontend — checkout + confirmación (D6)

- [x] 6.1 **[Red]** Tests (MSW): iniciar pago devuelve redsysUrl+params → `CheckoutRedsysPage` construye el form y (mock) auto-submit; `PagoConfirmadoPage` consulta el estado del pago del backend (no confía en la URL de retorno) y muestra PAID/FAILED/procesando
- [x] 6.2 **[Green]** `CheckoutRedsysPage` (mockup 09, auto-submit del form firmado al TPV; datos de tarjeta fuera) + `PagoConfirmadoPage` (mockup 10, UrlOK/UrlKO → estado real por backend) + service de pagos + rutas
- [x] 6.3 **[Green]** Activar "pagar ahora" en `MisReservasPage` (hoy deshabilitado): llama a iniciar pago y redirige al checkout

## 7. QA

- [x] 7.1 **[Refactor]** Limpieza manteniendo verde — sin deuda; paquete `pagos` propio, firma encapsulada
- [x] 7.2 `security-auditor`: firma en tiempo constante, webhook sin efectos antes de validar, no exposición de secretos/tarjeta en logs (RN-PAY-03), idempotencia, rate limit del webhook
  - **Fixes de seguridad aplicados (2026-07-05, TDD Red→Green):**
    - **MEDIO-1** Rate limit del webhook: `POST /api/pagos/webhook` ahora limitado a 60/min por IP en `RateLimitFilter` (público, sin JWT). Test `RateLimitFilterWebhookTest` (dentro/fuera del límite, por-IP).
    - **MEDIO-2** Carrera en idempotencia: `findByRedsysOrderIdForUpdate` (`@Lock PESSIMISTIC_WRITE`, sin migración ni `@Version`) en puerto/adapter/repo de Payment; el webhook lo usa dentro de `@Transactional`. La 2ª notificación espera, re-lee PAID y no reprocesa. Tests: finder con lock + un solo `markPaid`/`PAYMENT_CONFIRMED` en duplicado.
    - **MEDIO-3** Log-injection/XSS: `Ds_Order` sanitizado (`^[0-9A-Za-z]{1,32}$` → si no cumple, `<invalid-format>`) en TODOS los puntos de auditoría. Tests con `<script>` y salto de línea.
    - **BAJO-2** `Ds_SignatureVersion` != `HMAC_SHA256_V1` → rechazado como firma inválida antes de verificar. Test incluido.
  - Verificado: 11 tests unitarios de `ProcesarWebhookServiceTest` + 3 de `RateLimitFilterWebhookTest` en verde; boot del contexto Spring (JPA `@Query`/`@Lock`) validado contra Postgres real (IT en verde).
- [x] 7.3 `verification-specialist`: PASS — 29 unit backend + ArchUnit + migración V13 (contra PG real) + frontend 27/27; tsc/eslint limpios; contrato `amount` en euros coherente front↔back. Probes de firma inválida/duplicado/no-owner OK
- [ ] 7.4 `reality-checker`: journey de pago end-to-end contra el sandbox Redsys (iniciar → TPV test → webhook → PAID) — **PENDIENTE de live E2E: requiere despliegue + túnel público para el webhook; cubierto a nivel de firma/contrato por unit tests con vectores sandbox + tests MSW**
- [x] 7.5 `docs/openapi.yaml` actualizado con todos los endpoints `/pagos/*` (iniciar, webhook, efectivo, historial) alineados a la implementación real
