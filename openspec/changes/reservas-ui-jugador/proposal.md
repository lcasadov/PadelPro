## Why

El backend de `reservas` está completo y en producción, pero el flujo end-to-end del jugador no se puede completar desde la web: el botón "Reservar pista" del Home no navega a ningún sitio y no existen pantallas para buscar disponibilidad, confirmar, listar ni cancelar reservas. Sin esta capa de interfaz, la funcionalidad central del producto (reservar una pista) es inaccesible para el usuario final aunque la API ya lo soporte.

El grueso es frontend, pero la exploración detectó un **delta pequeño de backend imprescindible**: el endpoint de disponibilidad devuelve tramos con `plazasLibres` (1..max), donde un tramo con `plazasLibres < max` ya tiene una reserva incompleta y **solo admite unirse** (fuera de alcance), no crear. Para que la UI de "crear" solo ofrezca franjas realmente reservables sin replicar reglas de negocio en el cliente, el backend debe marcar cada tramo con un flag `creable`.

## What Changes

- **Delta backend (`disponibilidad-pistas`)**: añadir el flag `creable` a cada `TramoDisponible` (`true` sii `plazasLibres == max_participants`, es decir sin ocupación) en `DisponibilidadService` y en `docs/openapi.yaml`. Cambio aditivo y retrocompatible.
- **Nueva página "Buscar disponibilidad"** (mockup 03): selector de fecha + lista de tramos desde `GET /api/reservas/disponibles`; la UI ofrece "Reservar" **solo en los tramos `creable`** (los parciales, unibles, quedan fuera de alcance hasta `partidas`); seleccionar un tramo reservable lleva a confirmar. Pista en mantenimiento → lista vacía sin revelar el motivo.
- **Nueva página "Confirmar reserva"** (mockup 04): fecha/hora/duración elegidas, participantes adicionales opcionales (solo `externalName`), **precio total devuelto por el backend** (nunca calculado en cliente, RN-RES-03), confirmar → `POST /api/reservas` con `Idempotency-Key`; éxito → mensaje "reserva pendiente de confirmación por el club" (pago CASH, sin pantalla Redsys).
- **Nueva página "Mis reservas"** (mockup 05): lista con estado de reserva (PENDING_CONFIRMATION/CONFIRMED/CANCELLED/COMPLETED) y estado del pago.
- **Nueva página "Detalle de reserva"** (mockup 13): datos completos; botón cancelar solo si el usuario es owner y el estado es cancelable; fuera de plazo → mensaje claro del 422 (`CANCELLATION_DEADLINE_PASSED`) indicando que no aplica reembolso (RN-RES-04).
- **Conexión del botón "Reservar pista"** del Home a la búsqueda de disponibilidad.
- **Nuevo servicio `reservasApi.ts`** (axios, patrón existente `fn(token, …)` con `withCredentials`) con los 4 endpoints de jugador y manejo de errores por el campo `code` del error shape real `{ code, message, errors[] }`: 409 `CONFLICT` → "la franja se acaba de ocupar" + refrescar; 400 `VALIDATION_ERROR` / 422 `PARTICIPANTS_LIMIT_EXCEEDED`/`INVALID_STATE_TRANSITION`/`CANCELLATION_DEADLINE_PASSED` → mensajes específicos; 403/404 en detalle → acceso denegado / no encontrada.
- **`AuthContext` cachea el `userId`** (vía `getMeApi`, lazy) para determinar la propiedad de una reserva (`me.id == ownerId`) y decidir la visibilidad del botón cancelar.
- **Rutas privadas** nuevas en `App.tsx` protegidas por `PrivateRoute`.

Sin migraciones y sin secrets nuevos. El único cambio de backend es aditivo (flag `creable` en disponibilidad + su reflejo en `docs/openapi.yaml`); el resto de la API ya está documentada y desplegada.

## Capabilities

### New Capabilities
<!-- Ninguna capability nueva: se extienden dos existentes. -->

### Modified Capabilities
- `reservas`: se añade la capa de interfaz web del jugador (búsqueda de disponibilidad, confirmación, listado y cancelación desde la UI). Los requirements de backend de creación/cancelación no cambian; se añaden requirements de comportamiento de la interfaz de usuario que consume la API existente.
- `disponibilidad-pistas`: se añade el flag `creable` a cada `TramoDisponible` para distinguir tramos reservables (vacíos) de tramos parciales (solo unibles). Cambio aditivo del contrato de respuesta.

## Fase

🟢 Fase 1 (la capability `reservas` es Fase 1).

## Impact

- **Backend (delta aditivo)**: `TramoDisponible` (record) + `DisponibilidadService` (setear `creable = plazasLibres == maxParticipants`) + `docs/openapi.yaml` (campo `creable` en el schema `TramoDisponible`). Sin migraciones ni cambios en la lógica de cálculo existente.
- **Frontend nuevo**: `reservasApi.ts`; páginas `DisponibilidadPage`, `ConfirmarReservaPage`, `MisReservasPage`, `DetalleReservaPage` (+ CSS modules); rutas privadas en `App.tsx`; enlace desde `HomePage`.
- **Frontend modificado**: `AuthContext` (cachear `userId` vía `getMeApi`).
- **Stack**: React 18 + Vite + TS; reutiliza `AuthContext`, `PrivateRoute`, patrón de services axios (`fn(token, …)`, `withCredentials`) y design tokens existentes (cream/ink/lime, Bricolage).
- **Tests**: vitest + testing-library + MSW en frontend; test unitario del backend para `creable`. TDD estricto (test antes de implementar).
- **Sin impacto** en base de datos, infraestructura ni en el resto del API contract.

## Fuera de alcance

- Vistas admin de reservas (calendario semanal mockup 18, tabla del club mockup 19, confirmar/cambiar estado desde UI) — change posterior. Hoy el admin confirma vía API/`PATCH`.
- Pago online Redsys y pantalla "pago confirmado" (mockup 12) — capability `pagos-redsys`.
- Unirse a partidas / plazas libres de otros — capability `partidas`.
- Notificaciones de reserva (email/Telegram) — capability `notificaciones`.
- Cambios en el cálculo de disponibilidad o en cualquier endpoint de la API.
