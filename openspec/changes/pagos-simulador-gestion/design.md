## Context

`pagos-redsys` ya tiene: `RegistrarPagoEfectivoService` (marca PAID en efectivo, `POST /api/admin/pagos/{id}/efectivo`), `ProcesarWebhookService` (marca PAID por webhook Redsys firmado), `IniciarPagoService` (devuelve el formulario Redsys o `REDSYS_NOT_CONFIGURED`), `PagoQueryService.listAll()` (`GET /api/admin/pagos`), y `ReservaResponse.pago` (estado del pago expuesto al frontend). `PaymentStatus`: PENDING/PAID/REFUND/CANCELLED/FAILED.

## Goals / Non-Goals

**Goals:** gestión admin de cobros + marcar efectivo; estado de pago persistente en la UI del jugador; un simulador de pago online provisional (tarjeta/caducidad/CVC, aleatorio) que marque PAID; E2E de pago determinista.
**Non-Goals:** integración real con Redsys (formulario firmado/redirección/credenciales), pago compartido, reembolsos.

## Decisions

### D1 — Simulador provisional, aislado del pago real (reversible)
El simulador NO toca `IniciarPagoService` (queda para el Redsys real). Se añade un `SimularPagoService` + `POST /api/pagos/simular` separado. La UI "pagar ahora" enruta al simulador mientras Redsys no esté configurado; cuando se integre Redsys real, se conmuta a `iniciar` sin borrar nada. Así el provisional es reversible y no contamina la ruta definitiva.

### D2 — Resultado del simulador: aleatorio con tarjetas mágicas para test (RN nueva)
`POST /api/pagos/simular {reservaId, cardNumber, expiry, cvc}` (dueño de la reserva, reserva con pago PENDING). Decisión:
- **Tarjeta mágica APROBAR** (p. ej. `4111 1111 1111 1111`) → siempre APPROVED.
- **Tarjeta mágica RECHAZAR** (p. ej. `4000 0000 0000 0002`) → siempre DECLINED.
- Cualquier otra → aleatorio con **~85% APPROVED**, resto DECLINED.
Validación básica de formato (longitud tarjeta, MM/AA futura, CVC 3 dígitos) → 400 si inválido. La aleatoriedad usa una fuente inyectable/semilla para poder testear.
- **APPROVED** → marca el pago `PAID` (reutiliza la misma transición que efectivo/webhook, registrando método/gateway = SIMULADO). Devuelve `{resultado:"APPROVED"}`.
- **DECLINED** → deja el pago PENDING, `{resultado:"DECLINED", motivo}`.
Nunca persiste el número de tarjeta/CVC (RN-RGPD-04): solo se usa para decidir y se descarta; no se loggea.

### D3 — Gestión admin de cobros (reutiliza lo existente)
`AdminPagosPage` consume `GET /api/admin/pagos` (lista de pagos con estado + reserva + importe) para mostrar todas las reservas y filtrar Pagadas/Pendientes. "Marcar como pagada (efectivo)" llama a `POST /api/admin/pagos/{reservaId}/efectivo` (ya existe) y refresca. No requiere endpoint backend nuevo si `PagoHistorialResponse` ya trae lo necesario (titular/importe/estado/fecha); si falta algún campo, se amplía ese DTO.

### D4 — Estado de pago persistente en el jugador
`MisReservasPage` muestra un badge de estado (`Pendiente`/`Pagado`) por reserva leyendo `ReservaResponse.pago.status`. "Pago en diferido" deja de ser texto efímero: confirma el cobro presencial y la reserva sigue mostrando `Pendiente` de forma persistente (tras recargar). No requiere endpoint nuevo (el estado ya viene del backend).

### D5 — E2E determinista con tarjetas mágicas
Los E2E usan las tarjetas mágicas (aprobar/rechazar) para no depender del azar. `pago-efectivo`: admin marca pagada → PAID. `pago-ahora-simulador`: dueño reserva → pagar ahora → simulador → tarjeta-aprobar → OK/PAID; tarjeta-rechazar → KO.

## Risks / Trade-offs

- **[Confundir el simulador con pago real]** → Mitigación: método/gateway = `SIMULADO` en el registro; copy claro en la UI ("pago simulado"); provisional documentado; reversible (D1).
- **[Datos de tarjeta]** (RN-RGPD-04) → Mitigación: no se persisten ni loggean; solo deciden el resultado en memoria.
- **[Aleatoriedad rompe tests]** → Mitigación (D2/D5): tarjetas mágicas deterministas + fuente de azar inyectable.
- **[Marcar PAID dos veces]** → Mitigación: `simular` exige pago PENDING; si ya está PAID, rechaza/no-op idempotente.

## Migration Plan

1. Backend: `SimularPagoService` + endpoint; (si falta) ampliar `PagoHistorialResponse`.
2. Frontend: `SimuladorPagoPage`, `AdminPagosPage`, estado persistente en `MisReservasPage`, enrutar "pagar ahora" al simulador.
3. E2E: specs de efectivo y simulador.
4. Rollback: retirar el simulador y su ruta (el efectivo, el estado y la gestión admin quedan). Cuando haya Redsys real, conmutar "pagar ahora" a `iniciar`.
