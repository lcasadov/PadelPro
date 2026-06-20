## Why

`reservas` es la capability central del producto (Wave 3, 8 SP): sin ella no hay reserva de pista ni pago, y bloquea toda la Wave 4 (pagos-redsys, notificaciones, partidas). La infraestructura de datos crítica ya está lista —`reservations` + `participants` con la barrera anti-solapamiento `EXCLUDE USING gist` (V7)— y el camino de pago en efectivo + confirmación manual del ADMIN permite un MVP end-to-end sin depender de Telegram ni Redsys.

Fase del producto: **fase-1**.

## What Changes

- **Fase 0 — completar `system_config`** (prerequisito): la migración V6 implementada divergió del `data-model.md` y le faltan dos campos que `reservas` necesita. Se añaden `price_per_hour` (precio backend, RN-RES-03) y `cancellation_deadline_hours` (política de cancelación, RN-RES-04).
- **Tabla `payments`** (nueva, `data-model.md` §3.5): creada atómicamente con cada reserva en `status=PENDING`, con `amount` congelado = `price_per_hour × duration_minutes / 60` (RN-RES-03, US-024).
- **Idempotencia** (nueva tabla `idempotency_keys`): `POST /api/reservas` con la misma `Idempotency-Key` por usuario devuelve la reserva existente sin duplicar (RN-RES-05).
- **Crear reserva** (`POST /api/reservas`): valida franja y datos, calcula precio en backend, crea reserva (`PENDING_CONFIRMATION`) + participantes + pago atómicamente. Anti-solapamiento por captura de la violación del constraint gist → `409 CONFLICT`.
- **Cancelar reserva** (`DELETE /api/reservas/{id}`): aplica la política de plazo `cancellation_deadline_hours` (con/sin reembolso), solo owner o ADMIN (RN-AUTH-02).
- **Gestión ADMIN**: `GET /api/admin/reservas` (listado completo) y `PATCH /api/admin/reservas/{id}/estado` (confirmación/cancelación manual, bypass de política — D-RES-03).
- **Consulta**: `GET /api/reservas` (propias, owner o participante) y `GET /api/reservas/{id}` (403 nunca 404 para no-owner/no-participante — prevención BOLA).
- **No** se renombra `max_participants_per_pista` (divergencia nominal interna coherente; renombrar sería puro churn).

## Capabilities

### New Capabilities
- `reservas`: ciclo de vida completo de la reserva (crear, consultar, cancelar, cambio de estado admin), idempotencia, cálculo de precio backend, creación atómica del pago y barreras anti-solapamiento. El spec ya está redactado en `openspec/specs/reservas/spec.md` con 5 requirements; este change los implementa.

### Modified Capabilities
- `configuracion-club`: se añaden los requisitos de configuración de `price_per_hour` y `cancellation_deadline_hours` (lectura y edición por ADMIN), ausentes en la implementación actual.

## Impact

- **Migraciones Flyway**: `V8__add_pricing_to_system_config.sql`, `V9__create_payments_and_idempotency.sql`.
- **Backend** — paquete `com.padelpro.reservas` (extiende el lado lectura ya existente):
  - Dominio: `Payment`, enums `PaymentMethod`/`PaymentStatus`/`PaymentGateway`, máquina de estados de reserva, puertos de escritura.
  - Aplicación: `CrearReservaService`, `CancelarReservaService`, `ReservaQueryService`, `AdminReservaService`.
  - Infraestructura: repositorios JPA (`payments`, `idempotency_keys`), controllers `ReservaController` y `AdminReservaController`, traducción de la violación gist a `409`.
  - `auth`: +2 campos en `SystemConfig`, `SystemConfigResponse`, `UpdateSystemConfigRequest`, `SystemConfigService` (additivo).
- **API** (`docs/openapi.yaml`): endpoints de reservas ya contemplados; verificar/añadir `Idempotency-Key` header y esquemas de `payments`.
- **Caché**: la creación/cancelación de reserva invalida la caché de `disponibilidad-pistas` para la fecha afectada (integración ya prevista por `DisponibilidadCacheInvalidator`).
- **Sin cambios** en Telegram/Redsys: `payment_gateway=CASH` (default existente) cubre el MVP.

## Fuera de alcance

- Integración de pago online **Redsys** (HMAC, webhook, URL de TPV) → capability `pagos-redsys` (Wave 4A).
- Confirmación/cancelación vía **OTP Telegram** → `auth-otp-telegram` (Wave 2B) y `notificaciones` (Wave 4B). En este change la confirmación es manual por ADMIN.
- Vista de **partidas joinables** → `partidas` (Wave 4C).
- Reembolso real en pasarela: aquí solo se marca `payments.status=REFUNDED`/proceso iniciado; la ejecución del reembolso Redsys es de Wave 4A.
- Renombrado de `max_participants_per_pista` a `max_participants`.
