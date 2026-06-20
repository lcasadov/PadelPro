## 0. Gestión y arranque (orquestador)

- [ ] 0.1 Asegurar/crear Issue de US-007 en GitHub y obtener su número
- [ ] 0.2 Crear rama `feat/backend/<id>-reservas` desde `develop` y push
- [ ] 0.3 Mover el item del Project v2 a "In Progress"

## 1. Fase 0 — Completar system_config (additivo, configuracion-club)

- [x] 1.1 Migración `V8__add_pricing_to_system_config.sql`: `ADD price_per_hour NUMERIC(12,2) DEFAULT 15.00` + `CHECK (price_per_hour > 0)`; `ADD cancellation_deadline_hours INTEGER NOT NULL DEFAULT 2` + `CHECK (cancellation_deadline_hours >= 0)`
- [x] 1.2 Entidad `SystemConfig`: añadir campos `pricePerHour` (BigDecimal) y `cancellationDeadlineHours` (Integer) + getters/setters/builder
- [x] 1.3 `SystemConfigResponse`: exponer ambos campos
- [x] 1.4 `UpdateSystemConfigRequest`: aceptar ambos campos con validación (`@Positive`, `@PositiveOrZero`)
- [x] 1.5 `SystemConfigService`: mapeo y persistencia de los nuevos campos
- [x] 1.6 Tests de `GET`/`PATCH` config con los campos nuevos y validaciones de borde

## 2. Esquema de datos — payments e idempotencia

- [x] 2.1 Migración `V9__create_payments_and_idempotency.sql`: enums `payment_method`/`payment_status`/`payment_gateway`; tabla `payments` (data-model §3.5) con FK UNIQUE a `reservations`, `chk_pay_amount`, `chk_pay_cash_admin` e índices
- [x] 2.2 En la misma migración: tabla `idempotency_keys(key, user_id, reservation_id, created_at)` con `UNIQUE(user_id, key)` e índice por `created_at`
- [x] 2.3 Entidad `Payment` + enums de dominio `PaymentMethod`/`PaymentStatus`/`PaymentGateway`
- [x] 2.4 Entidad/repositorio `IdempotencyKey`
- [x] 2.5 Repositorios JPA `PaymentJpaRepository` e `IdempotencyKeyJpaRepository`

## 3. Dominio de escritura de reservas

- [x] 3.1 Puertos de salida de escritura (`ReservationCommandPort`, `PaymentCommandPort`)
- [x] 3.2 Máquina de estados de reserva (transiciones válidas; rechazo de inválidas con 422)
- [x] 3.3 Lógica de cálculo de precio: `amount = price_per_hour × duration_minutes / 60` (congelado)
- [x] 3.4 Política de cancelación basada en `cancellation_deadline_hours` (con/sin reembolso)

## 4. Crear reserva (POST /api/reservas)

- [x] 4.1 `CrearReservaService`: validación de entrada (fecha futura, duración ∈ {60,90,120,150,180}, startTime en :00/:30, límite de participantes)
- [x] 4.2 Idempotencia: consulta `(user_id, Idempotency-Key)` → si existe, devolver reserva existente sin duplicar
- [x] 4.3 Creación atómica: reserva `PENDING_CONFIRMATION` + participantes (owner slot 1) + `payments` PENDING en una transacción
- [x] 4.4 Traducción de la violación del constraint `excl_res_no_overlap` a `409 CONFLICT`
- [x] 4.5 Invalidar caché de disponibilidad para la fecha afectada al commit
- [x] 4.6 `ReservaController` POST + DTOs de request/response (priceTotal desde backend)

## 5. Cancelar y gestión

- [x] 5.1 `CancelarReservaService`: autorización (owner o ADMIN, RN-AUTH-02), aplicar política de plazo, marcar pago `REFUNDED` si procede
- [x] 5.2 `DELETE /api/reservas/{id}`: 204 / 403 / 422 según caso
- [x] 5.3 `AdminReservaService` + `PATCH /api/admin/reservas/{id}/estado` (bypass de política, transiciones válidas)
- [x] 5.4 Invalidar caché de disponibilidad al cancelar

## 6. Consulta

- [x] 6.1 `GET /api/reservas`: solo reservas donde el usuario es owner o participante (RN-AUTH-01), con `JOIN FETCH payments` (evitar N+1)
- [x] 6.2 `GET /api/reservas/{id}`: 403 (no 404) para no-owner/no-participante (prevención BOLA)
- [x] 6.3 `GET /api/admin/reservas`: listado completo (solo ADMIN)

## 7. API spec

- [x] 7.1 Actualizar `docs/openapi.yaml`: header `Idempotency-Key`, esquemas de `payments` y respuestas 201/400/403/409/422 de reservas

## 8. Testing

- [x] 8.1 Tests unitarios de cálculo de precio, máquina de estados y política de cancelación (`PriceCalculatorTest`, `ReservationStateMachineTest`, `CancellationPolicyTest` — 24 tests, verdes)
- [x] 8.2 Tests de integración de creación atómica (reserva + participantes + pago) — `ReservaControllerIntegrationTest` (Postgres real, base `PostgresIntegrationTest`). ✅ VERDE contra PG :5433 (23/23) tras corregir #159, #160 y el binding de enums nativos.
- [x] 8.3 Test de concurrencia: N `POST` simultáneos a la misma franja → exactamente un 201 y (N-1) 409 (constraint gist) — `ReservaSolapamientoConcurrencyIT` (HTTP real + thread pool). ✅ VERDE contra PG :5433 (1/1).
- [x] 8.4 Tests de idempotencia: misma key → mismo recurso, sin duplicado; key nueva → nueva reserva; scope por usuario — en `ReservaControllerIntegrationTest`. ✅ VERDE.
- [x] 8.5 Tests de autorización: BOLA en `GET /{id}` (403 no 404), cancelación por no-owner (403), listado admin por USER (403), 401 sin JWT — en `ReservaControllerIntegrationTest`. ✅ VERDE.
- [x] 8.6 Test US-024: cambiar `price_per_hour` no altera el `amount` de reservas existentes — en `ReservaControllerIntegrationTest`. ✅ VERDE.
- [ ] 8.7 Cobertura dentro del umbral JaCoCo — no medida en esta sesión (no se ejecutó `jacoco:report`). Los IT de reservas ya ejecutan; queda pendiente generar el informe de cobertura.

> **Nota de ejecución (actualizada 2026-06-18):** los unitarios 8.1 están verdes. Los IT de reservas 8.2–8.6 **ya ejecutan en VERDE** contra PostgreSQL real (`:5433`, Vía A) tras corregir tres bugs de producción:
> - **BUG-1 / #159 (CORREGIDO)**: `system_config.id` se creaba `INTEGER` (V6) pero la entidad `SystemConfig.id` es `Long` → arranque fallaba con `ddl-auto=validate`. Corregido con migración `V10__system_config_id_to_bigint.sql` (ALTER a BIGINT; sin tocar V6 ni la entidad). Verificado: V10 aplicada, columna ahora `bigint`.
> - **BUG-3 / #160 (CORREGIDO)**: `UserRepository.findById(Long)`/`save(User)` eran `default` que lanzaban `UnsupportedOperationException`; Spring Data no respalda métodos `default`, así que `UserStatusFilter` rompía cada request con JWT. Corregido eliminando los `default` (no había ambigüedad real con erasure). Verificado: compila sin ambigüedad; `UserStatusFilterTest` y todos los IT de auth verdes.
> - **BUG-4 (nuevo, CORREGIDO)**: las columnas enum nativas de Postgres (`reservation_status`, `reservation_channel`, `payment_method`, `payment_status`, `payment_gateway`) fallaban en cada INSERT (`column ... is of type <enum> but expression is of type character varying`) porque Postgres no castea varchar→enum implícitamente. Corregido con `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` + `columnDefinition` en `Reservation`, `Participant` y `Payment`.
> - **BUG-2 (corregido previamente por test-runner)**: `application-it.yml` no definía `app.encryption.key` → corregido en este change.
> Limitación de entorno: Docker Desktop no es accesible vía docker-java en este host, por lo que 8 IT preexistentes de **auth/usuarios** (que aún usan `@Testcontainers` directo, no la base portable `PostgresIntegrationTest`) fallan con "Could not find a valid Docker environment". No están relacionados con esta capability ni con los bugs anteriores; requieren migrarse a Vía A en un trabajo aparte.

## 9. Cierre

- [x] 9.1 `verification-specialist`: build + tests + probes → **PASS** (8 probes adversariales verdes; anti-overlap a nivel BD + concurrencia, precio backend-only, idempotencia, máquina de estados, BOLA, inmunidad SQLi)
- [x] 9.2 `reality-checker`: user journey reservar→confirmar(admin)→cancelar end-to-end → **READY** (A-, journey completo por HTTP real; destapó y corrigió #161 login roto antes de certificar)
- [x] 9.3 Commit y push a la rama (reservado al orquestador)
- [x] 9.4 PR #162 creada con `Closes #14` (https://github.com/lcasadov/PadelPro/pull/162). MERGEABLE; sin workflow de tests en CI (solo review CodeRabbit). Cobertura JaCoCo (8.7) pendiente de medir.
