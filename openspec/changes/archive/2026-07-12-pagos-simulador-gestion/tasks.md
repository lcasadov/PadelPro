## 1. Backend — simulador de pago

- [x] 1.1 `SimularPagoService`: valida reserva del dueño + pago PENDING; decide resultado (tarjeta mágica aprobar/rechazar deterministas; resto ~85% APPROVED con fuente de azar inyectable); valida formato tarjeta/MM-AA/CVC (400 si inválido); NUNCA persiste/loggea la tarjeta (RN-RGPD-04)
- [x] 1.2 APPROVED → marca el pago `PAID` (misma transición que efectivo/webhook; método/gateway = `SIMULADO`); DECLINED → deja PENDING + motivo; idempotente si ya PAID
- [x] 1.3 `POST /api/pagos/simular` (JWT, dueño) → `{resultado, motivo?}`
- [x] 1.4 Tests unitarios: aprobar/rechazar (mágicas), aleatorio con semilla, formato inválido, no-dueño (403), ya pagado

## 2. Backend — gestión admin (reutilizar)

- [x] 2.1 Verificar que `GET /api/admin/pagos` (`PagoHistorialResponse`) trae estado + reserva + importe + titular + fecha; si falta algo, ampliar el DTO/servicio — YA TRAE todo (status, reservaId, reservationDate, amount, ownerId=titular); sin cambios
- [x] 2.2 Confirmar `POST /api/admin/pagos/{reservaId}/efectivo` marca PAID (ya existe); test si se toca — sin tocar; ya existente y testado

## 3. Frontend — simulador y "pagar ahora"

- [x] 3.1 `SimuladorPagoPage` (checkout simulado): form nº tarjeta + caducidad MM/AA + CVC + "Pagar"; copy "pago simulado"; POST `/api/pagos/simular` → APPROVED redirige a OK (PAID) / DECLINED a KO (con reintento)
- [x] 3.2 `MisReservasPage`: "Pagar ahora" enruta al simulador (con reservaId/importe); mantener el flujo Redsys real detrás de una bandera para el futuro

## 4. Frontend — estado persistente + admin cobros

- [x] 4.1 `MisReservasPage`: badge de estado de pago (`Pendiente`/`Pagado`) por reserva desde `ReservaResponse.pago.status`; "pago en diferido" = confirmación de cobro presencial, la reserva sigue `Pendiente` de forma persistente
- [x] 4.2 `AdminPagosPage` (`/admin/pagos`, AdminRoute): lista de reservas/pagos (titular/importe/estado/fecha) + filtro Todas/Pagadas/Pendientes + "Marcar como pagada (efectivo)" en pendientes → refresca
- [x] 4.3 Enlace "Cobros" desde Home admin (solo ADMIN); rutas en App.tsx
- [x] 4.4 Tests frontend (vitest+MSW) de las páginas nuevas y del estado persistente

## 5. E2E

- [ ] 5.1 `pago-efectivo.spec.ts`: reservar → (admin) `/admin/pagos` → marcar pagada → estado PAID
- [x] 5.2 `pago-ahora-simulador.spec.ts`: reservar → "pagar ahora" → simulador → tarjeta-aprobar → OK/PAID; tarjeta-rechazar → KO
- [x] 5.1 `pago-efectivo.spec.ts`: reservar → `/admin/pagos` → marcar pagada → PAID
- [x] 5.3 Verificados contra el stack real: los 3 pasan (`3 passed`)

## 6. QA y cierre

- [x] 6.1 Backend `mvn -DskipITs test` verde; Frontend `tsc`+`lint`+`test`(227)+`build` verde; E2E de pago verde
- [x] 6.2 Contrato front↔back reconciliado: `PagoHistorialResponse` ampliado con `ownerName` (titular); frontend mapea `ownerName`→titular / `pagoId`→id
- [x] 6.3 PR

## Notas de implementación
- **Simulador provisional (D1):** "pagar ahora" enruta a `SimuladorPagoPage` (`USE_REDSYS=false`); `POST /api/pagos/simular` (dueño) decide con tarjetas mágicas (`4111…` aprueba, `4000…0002` rechaza; resto ~85% aprueba) y marca `PAID` (gateway SIMULADO). No persiste tarjeta/CVC (RN-RGPD-04). Reversible (poner `USE_REDSYS=true` cuando haya Redsys real).
- **Gestión admin:** `AdminPagosPage` (`/admin/pagos`, "Cobros" en Home admin) lista todos los pagos con estado/titular/importe/fecha, filtra Todas/Pagadas/Pendientes, y marca pagada (efectivo) las pendientes.
- **Estado persistente:** `MisReservasPage` muestra el `EstadoBadge` de pago (Pago pendiente/Pagado) — arregla el bug del "diferido" efímero.
- **Reconciliación:** se añadió `ownerName` a `PagoHistorialResponse` (join a users por `ownerId`) para que el panel muestre el nombre del titular, no un id.
