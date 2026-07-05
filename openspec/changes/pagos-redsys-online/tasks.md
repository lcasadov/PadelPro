# Pago online Redsys (fase-1, Wave 4A)

> Orden TDD estricto (Red → Green → Refactor) en cada grupo. Owner paga el total (pago compartido por participante DIFERIDO). Tests contra el entorno de pruebas Redsys (comercio sandbox); datos de tarjeta NUNCA en PadelPro (RN-PAY-03).

## 1. Config — `redsys_terminal` + descifrado (D4)

- [ ] 1.1 **[Red]** Test: `SystemConfig` expone `redsysTerminal` descifrado; default "1" si ausente
- [ ] 1.2 **[Green]** Migración Flyway `Vn__add_redsys_terminal_to_system_config.sql` (TEXT, nullable, cifrable) + campo en la entidad/DTO + descifrado con el servicio AES-256 existente

## 2. Backend — firma Redsys (D2)

- [ ] 2.1 **[Red]** Tests de `RedsysSignature` con vectores del sandbox: derivar clave por-pedido (3DES del `Ds_Merchant_Order`), calcular HMAC-SHA256 de `Ds_MerchantParameters`, round-trip firmar→verificar, comparación en tiempo constante (`MessageDigest.isEqual`)
- [ ] 2.2 **[Green]** Utilidad `RedsysSignature` (firmar/verificar); sin dependencia de terceros; nunca loguea la clave

## 3. Backend — iniciar pago (D5)

- [ ] 3.1 **[Red]** Tests `IniciarPagoService`: owner OK → IN_PROGRESS + form firmado + `PAYMENT_INITIATED`; no-owner → 403; reserva inexistente → 404; cancelada → 422; pago ya IN_PROGRESS/PAID → 409; importe del cliente ignorado (amount = backend en céntimos)
- [ ] 3.2 **[Green]** `IniciarPagoService` + `PagoController` `POST /api/pagos/iniciar` + DTOs (request `reservaId`, response `pagoId/redsysOrderId/redsysUrl/amount/status`); genera `redsys_order_id` único; persiste `payment_url`; auditoría; documentar en openapi

## 4. Backend — webhook (D3)

- [ ] 4.1 **[Red]** Tests `ProcesarWebhookService`: firma válida + aprobado → PAID + transaction_id + `PAYMENT_CONFIRMED`; firma válida + rechazo → FAILED + `PAYMENT_REJECTED`; firma inválida → 200 sin cambio + `PAYMENT_WEBHOOK_INVALID_SIGNATURE`; duplicado sobre PAID → 200 sin reproceso; order_id inexistente → 200 + alerta
- [ ] 4.2 **[Green]** `ProcesarWebhookService` + `POST /api/pagos/webhook` (sin JWT, allowlist en SecurityConfig, rate limit); valida firma ANTES de tocar nada; idempotente; siempre 200; nunca persiste/loguea datos de tarjeta; documentar en openapi

## 5. Backend — efectivo ADMIN + historial

- [ ] 5.1 **[Red]** Tests: ADMIN registra efectivo → PAID/CASH/registered_by + `PAYMENT_CASH_REGISTERED`; USER → 403; reserva ya PAID → 422. `GET /api/pagos` (propios) y `GET /api/admin/pagos` (todos)
- [ ] 5.2 **[Green]** `RegistrarPagoEfectivoService` + `AdminPagoController` (`POST /api/admin/pagos/{reservaId}/efectivo`, `GET /api/admin/pagos`); `PagoQueryService` + `GET /api/pagos`; documentar en openapi

## 6. Frontend — checkout + confirmación (D6)

- [x] 6.1 **[Red]** Tests (MSW): iniciar pago devuelve redsysUrl+params → `CheckoutRedsysPage` construye el form y (mock) auto-submit; `PagoConfirmadoPage` consulta el estado del pago del backend (no confía en la URL de retorno) y muestra PAID/FAILED/procesando
- [x] 6.2 **[Green]** `CheckoutRedsysPage` (mockup 09, auto-submit del form firmado al TPV; datos de tarjeta fuera) + `PagoConfirmadoPage` (mockup 10, UrlOK/UrlKO → estado real por backend) + service de pagos + rutas
- [x] 6.3 **[Green]** Activar "pagar ahora" en `MisReservasPage` (hoy deshabilitado): llama a iniciar pago y redirige al checkout

## 7. QA

- [ ] 7.1 **[Refactor]** Limpieza manteniendo verde
- [ ] 7.2 `security-auditor`: firma en tiempo constante, webhook sin efectos antes de validar, no exposición de secretos/tarjeta en logs (RN-PAY-03), idempotencia, rate limit del webhook
- [ ] 7.3 `verification-specialist`: build + tests (backend maven, frontend vitest) + lint; probes de firma inválida/duplicado/no-owner
- [ ] 7.4 `reality-checker`: journey de pago end-to-end contra el sandbox Redsys (iniciar → TPV test → webhook → PAID) — live E2E puede diferirse si requiere despliegue/túnel para el webhook
- [ ] 7.5 Actualizar `docs/openapi.yaml` y verificar coherencia de todos los endpoints `/pagos/*`
