## Why

Tres carencias en el flujo de pagos, reportadas por el usuario:
1. El ADMIN no tiene una vista para **ver todas las reservas con su estado de pago** ni para **marcar como pagadas (efectivo)** las pendientes desde un sitio central (el cobro presencial es habitual).
2. Elegir **"pago en diferido"** en Mis Reservas **no se refleja** en la reserva: es un texto efímero que desaparece al recargar; el estado de pago no se muestra de forma persistente.
3. **"Pagar ahora"** falla siempre con `REDSYS_NOT_CONFIGURED` porque Redsys no está configurado, y no hay forma de probar el pago online. Hasta integrar Redsys real, se necesita un **simulador**.

## What Changes

- **Página admin `/admin/pagos` (Cobros):** lista todas las reservas/pagos con estado (Pagado/Pendiente), importe y titular; filtro Todas/Pagadas/Pendientes; botón **"Marcar como pagada (efectivo)"** en las pendientes (usa `POST /api/admin/pagos/{reservaId}/efectivo`). Solo ADMIN; enlace desde Home admin.
- **Estado de pago persistente en Mis Reservas:** cada reserva muestra su estado (`Pendiente`/`Pagado`) leído de `ReservaResponse.pago`. "Pago en diferido" pasa a ser una confirmación de que el cobro será presencial y la reserva **queda visiblemente Pendiente** (no un texto que se pierde).
- **Simulador de pago (sustituye al TPV externo hasta integrar Redsys):**
  - Frontend: pantalla de checkout simulada que pide **nº de tarjeta, caducidad (MM/AA) y CVC**, con "Pagar".
  - Backend: `POST /api/pagos/simular` (dueño de la reserva) que decide el resultado —**aprobado con mayor probabilidad, a veces rechazado**— y, al aprobar, marca el pago `PAID` (reutilizando la lógica de confirmación). **Tarjetas mágicas** deterministas para test: una que SIEMPRE aprueba y otra que SIEMPRE rechaza; el resto, aleatorio (~85% aprobar).
  - "Pagar ahora" enruta a este simulador (en vez de al `iniciar` de Redsys) mientras Redsys no esté configurado.
- **E2E de pago:** efectivo (ADMIN marca pagada → PAID) y pago-ahora vía simulador (tarjeta que aprueba → PAID → pantalla OK; tarjeta que rechaza → KO).

## Capabilities

### New Capabilities
<!-- Ninguna: extiende la capability pagos-redsys ya existente (efectivo, iniciar, webhook, historial). -->

### Modified Capabilities
<!-- pagos-redsys: se añade el simulador de pago online (provisional) y la gestión admin de cobros; sin cambiar los requisitos del pago real Redsys (diferido). -->

## Impact

- **Backend (`pagos`):** nuevo `SimularPagoService` + endpoint `POST /api/pagos/simular`; reutiliza la confirmación de pago (marca `PAID`). Sin cambios de esquema (usa `payments`/`PaymentStatus`).
- **Frontend:** nueva `AdminPagosPage` (/admin/pagos) + `SimuladorPagoPage` (checkout simulado); `MisReservasPage` muestra estado de pago persistente y enruta "pagar ahora" al simulador.
- **E2E:** nuevos specs de pago (efectivo + simulador).
- **Fase del producto:** fase-1 (pagos-redsys, provisional hasta credenciales reales).

## Fuera de alcance

- **Integración real con el TPV de Redsys** (formulario firmado + redirección + webhook con credenciales reales): se termina "más adelante"; el simulador es provisional.
- Pago compartido entre participantes (diferido de la capability).
- Reembolsos.
