## Context

`reservas` es el núcleo transaccional del sistema. La infraestructura de datos crítica ya existe (V7): tablas `reservations` y `participants` con el constraint `EXCLUDE USING gist` (`excl_res_no_overlap`) que garantiza a nivel de BD que dos reservas activas no solapen la misma franja (D-RES-01, RN-RES-01). El lado de lectura (`DisponibilidadService`, `ReservationQueryPort`) también está implementado por la capability `disponibilidad-pistas`.

Faltan: el lado de escritura (crear/cancelar/cambiar estado), la tabla `payments`, la idempotencia, y dos campos de configuración (`price_per_hour`, `cancellation_deadline_hours`) que la migración V6 omitió respecto al `data-model.md`. El MVP debe funcionar sin Telegram ni Redsys.

Restricciones: Spring Boot 3 + JDK 17, arquitectura hexagonal (dominio/aplicación/infraestructura), Flyway con `ddl-auto=validate`, PostgreSQL 15 (prod) y H2 en modo PostgreSQL (tests). Identidad de commits: `orquestadoria`.

## Goals / Non-Goals

**Goals:**
- Crear reserva con cálculo de precio en backend y creación atómica del pago (RN-RES-03).
- Garantizar no-solapamiento bajo concurrencia (RN-RES-01) y respuesta `409` limpia.
- Idempotencia de `POST /api/reservas` por `Idempotency-Key` y usuario (RN-RES-05).
- Política de cancelación configurable con/sin reembolso (RN-RES-04) y autorización estricta (RN-AUTH-01, RN-AUTH-02).
- MVP end-to-end con `payment_gateway=CASH` y confirmación manual ADMIN (D-RES-02, D-RES-03).

**Non-Goals:**
- Pago online Redsys, OTP Telegram, partidas joinables (Waves posteriores).
- Ejecución real del reembolso en pasarela (solo marca de estado).
- Renombrado de `max_participants_per_pista`.

## Decisions

### D1 — Anti-solapamiento: capturar la violación del constraint gist (RN-RES-01)
La creación intenta el `INSERT` y, si la BD lanza la violación de `excl_res_no_overlap`, se traduce a `409 CONFLICT`. El constraint gist (V7) ya garantiza atomicidad y unicidad bajo cualquier condición de carrera, por lo que un `SELECT FOR UPDATE` adicional es redundante.
- **Alternativas:** (B) `SELECT FOR UPDATE` sobre `system_config(id=1)` → serializa todas las reservas del club (cuello de botella); (C) advisory lock por franja → más complejo, beneficio marginal sobre la captura del constraint.
- **Razón:** la doble barrera del spec se honra con gist (BD) + validación funcional previa de disponibilidad (aplicación); la barrera infalible es el gist.

### D2 — Idempotencia con tabla dedicada `idempotency_keys` (RN-RES-05)
Tabla `idempotency_keys(key, user_id, reservation_id, created_at)` con `UNIQUE(user_id, key)`. En `POST /api/reservas`, si existe la pareja `(user_id, key)` se devuelve la reserva referenciada sin crear duplicado.
- **Alternativas:** columna `idempotency_key UNIQUE` en `reservations` → clave global o índice compuesto, no reutilizable por otros endpoints, sin TTL.
- **Razón:** separar la idempotencia del agregado permite TTL/expiración y reutilización futura (cancelación, pagos), y el alcance correcto es por-usuario.

### D3 — Pago atómico y precio congelado (RN-RES-03, US-024)
La reserva y su `payments` (1:1, `status=PENDING`) se crean en la misma transacción. `amount = price_per_hour × duration_minutes / 60` se calcula en backend e se congela; cambios posteriores de `price_per_hour` no afectan reservas existentes. El importe enviado por el cliente se ignora.
- **Razón:** el precio es responsabilidad exclusiva del servidor; el snapshot evita que un cambio de tarifa altere reservas ya hechas.

### D4 — Completar `system_config` de forma additiva (Fase 0)
Migración V8 añade `price_per_hour NUMERIC(12,2) DEFAULT 15.00 CHECK > 0` y `cancellation_deadline_hours INTEGER NOT NULL DEFAULT 2 CHECK >= 0`. La fila singleton existente toma el default sin pasos manuales. Se exponen en `SystemConfigResponse`/`UpdateSystemConfigRequest` para que el ADMIN los gestione.
- **Alternativas:** mini-change aparte → ceremonia innecesaria, solo `reservas` consume estos campos; `NOT NULL` sin default → falla la migración sobre la fila ya insertada.
- **Razón:** additivo, idempotente y sin downtime; el default 15.00 proviene del `data-model.md`.

### D5 — MVP con CASH + confirmación manual ADMIN (D-RES-02, D-RES-03)
Con `payment_gateway=CASH` (default), la reserva nace `PENDING_CONFIRMATION` y el pago `PENDING`. El ADMIN confirma vía `PATCH /api/admin/reservas/{id}/estado`, lo que permite probar el flujo completo sin pasarela ni OTP. La política de plazo de cancelación no aplica a operaciones admin.

### D6 — Máquina de estados unidireccional
`PENDING_CONFIRMATION → CONFIRMED → COMPLETED`, con `→ CANCELLED` desde los dos primeros. `COMPLETED` y `CANCELLED` son terminales; transiciones inversas devuelven `422`. La autorización de lectura/cancelación sigue RN-AUTH-01/02; el detalle a no-owner/no-participante devuelve `403` (no `404`) para prevenir BOLA.

## Risks / Trade-offs

- **Traducción de la violación gist** → si la excepción de PostgreSQL no se mapea con precisión (nombre del constraint), podría devolverse `500` en vez de `409`. **Mitigación:** test de concurrencia que fuerza el solapamiento y verifica `409`; mapear por nombre de constraint `excl_res_no_overlap`.
- **Divergencia H2 vs PostgreSQL** → el constraint gist y `tsrange` no existen en H2; los tests de solapamiento real deben correr contra PostgreSQL (Testcontainers), no H2. **Mitigación:** cubrir el anti-overlap en tests de integración con Testcontainers; H2 solo para lógica de aplicación.
- **Ventana de idempotencia sin TTL definido** → claves acumuladas indefinidamente. **Mitigación:** definir TTL (p. ej. 24 h) y limpieza diferida; documentar como deuda menor si no se implementa la purga en este change.
- **`payments.amount` congelado vs cambio de tarifa** (US-024) → si el snapshot falla, el titular podría pagar una tarifa distinta. **Mitigación:** calcular y persistir `amount` en la misma transacción de creación; test que cambia `price_per_hour` y verifica que reservas previas conservan su importe.
- **Caché de disponibilidad desincronizada** → tras crear/cancelar, la disponibilidad podría mostrar datos viejos. **Mitigación:** invalidar la caché de la fecha afectada en el commit de la transacción (`DisponibilidadCacheInvalidator`).

## Migration Plan

1. V8 (`system_config` additivo) — sin downtime, la fila singleton toma defaults.
2. V9 (`payments` + `idempotency_keys` + enums `payment_*`) — tablas nuevas, sin impacto en datos existentes.
3. Despliegue del backend con `ddl-auto=validate`: las entidades nuevas deben casar exactamente con las migraciones.
4. **Rollback:** las migraciones son additivas; revertir = `DROP` de tablas/columnas nuevas. No hay pérdida de datos de reservas porque `reservations`/`participants` ya existían (V7) y no se modifican.

## Open Questions

- TTL concreto de `idempotency_keys` y si la purga entra en este change o se difiere.
- ¿`GET /api/reservas` incluye `JOIN FETCH payments` (relación 1:1) para evitar N+1? (recomendado por `data-model.md` Q-perf).
- Confirmar en `docs/openapi.yaml` el esquema de `Idempotency-Key` (header) y de la respuesta de `payments`.
