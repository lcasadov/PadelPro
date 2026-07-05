## Why

La capability `partidas` está definida (spec + mockups 20/21/22) pero **sin implementar**: no existe el endpoint `POST /api/reservas/{id}/unirse`, ni servicio de unión, ni pantallas. Hoy un jugador no puede ver las reservas con plazas libres ni apuntarse a una partida de otro. Es el siguiente candidato de Wave 4 (baja complejidad, reutiliza `reservas` + `disponibilidad-pistas`) y desbloquea el uso social del club (partidas abiertas).

La exploración detectó dos restricciones que acotan el alcance: (1) `TramoDisponible` **no expone el `reservaId`**, así que la UI no puede identificar a qué reserva unirse — hay que exponer las reservas joinable con su id; (2) el "pago por participante" que muestran los mockups ("Tu parte 4,50€") **no está modelado** (`Payment` es 1:1 con la reserva y del owner) y depende de `pagos-redsys` (no construido) — se difiere, igual que se hizo en `reservas`.

## What Changes

- **[Backend] Exponer reservas joinable con su `reservaId`.** Un jugador debe poder listar las partidas abiertas (reservas activas con plazas libres) identificables por id, fecha, hora, duración, plazas libres y participantes. Se resuelve con un endpoint de listado de partidas abiertas (o exponiendo el `reservaId` en la disponibilidad; ver design D1).
- **[Backend] `POST /api/reservas/{id}/unirse`.** Añade al usuario autenticado como participante adicional (`is_owner=false`, siguiente `slot_position`). Validaciones: 404 si la reserva no existe; 422 si su estado no admite unión (solo `PENDING_CONFIRMATION`/`CONFIRMED`; `CANCELLED`/`COMPLETED` → 422); 409 si el usuario ya es participante (RN-AUTH-03); 422 si la reserva está completa (RN-RES-02). Devuelve `UnirseResponse` (participanteId, reservaId, userId, nombre, statusPago).
- **[Backend] Abandonar una partida.** El participante no-owner puede salir de una reserva a la que se unió (libera su plaza). (Ver design D3 — incluible en el MVP o diferible.)
- **[Frontend] Pantallas de partidas** (mockups 21/22): "Partidas abiertas" (lista de reservas joinable) y "Confirmar unión" (detalle + unirse). El importe "tu parte" se muestra **informativo** (total ÷ participantes); la unión NO cobra online (pago presencial/diferido, como en `reservas`).
- **[Frontend] Navegación**: acceso a "Partidas abiertas" desde Home/reservas; rutas privadas nuevas.
- **Requirement 3 de la spec (listar reservas donde participo) YA funciona** (`GET /api/reservas` incluye participaciones no-owner) — no requiere trabajo.

## Capabilities

### New Capabilities
<!-- Ninguna capability nueva: `partidas` ya existe como spec; este change la implementa. -->

### Modified Capabilities
- `partidas`: se implementan sus requirements (ver reservas joinable, unirse, abandonar) sobre el modelo de `reservas` existente.
- `disponibilidad-pistas`: se expone el `reservaId` (y datos mínimos) de las reservas joinable para que la UI pueda unirse — cambio aditivo del contrato (o endpoint nuevo; ver design).

## Impact

- **Fase del producto**: fase-1 (`partidas` es fase-1).
- **Backend** (`com.padelpro.reservas`): nuevo `UnirseReservaService` + endpoint en `ReservaController`; puerto de comando para añadir participante a reserva existente (hoy solo hay `save` de creación completa); DTO `UnirseResponse`; cálculo de siguiente `slot_position`; validaciones 404/409/422; invalidación de caché de disponibilidad; listado de partidas abiertas (endpoint o extensión de disponibilidad) exponiendo `reservaId`. Documentar en `docs/openapi.yaml` (el `/unirse` ya está documentado; alinear con lo implementado).
- **Frontend** (`frontend/src/`): páginas `PartidasAbiertasPage`, `ConfirmarUnionPage`; rutas en `reservasPaths.ts` y `App.tsx`; funciones en `reservasApi.ts` (listar partidas, unirse, abandonar).
- **Datos / migraciones**: previsiblemente **ninguna** (se reutilizan `reservations`/`participants`; el FK `participants.user_id → users` ya existe). Sin cambios de pago.
- **Sin impacto** en auth ni en el cálculo de precio existente.

## Fuera de alcance

- **Pago por participante / "tu parte" cobrada online** (Redsys) — depende del modelo de pago compartido (hoy `Payment` es 1:1 owner) y de `pagos-redsys`. Se difiere; en este change el importe se muestra informativo y la unión no cobra online.
- **"Crear partida pública" como concepto separado** (mockup 20) — en v1 no existe distinción pública/privada: *toda reserva con plazas libres es joinable* (per `spec.md`). Crear una partida = crear una reserva (flujo ya existente). No se añade flag público/privado.
- **Notificar al owner cuando alguien se une** (email/Telegram) — capability `notificaciones`.
- **Confirmación por OTP Telegram** de la unión — capability `auth-otp-telegram`.
