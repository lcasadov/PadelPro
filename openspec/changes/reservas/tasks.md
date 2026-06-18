## 0. Gestión y arranque (orquestador)

- [ ] 0.1 Asegurar/crear Issue de US-007 en GitHub y obtener su número
- [ ] 0.2 Crear rama `feat/backend/<id>-reservas` desde `develop` y push
- [ ] 0.3 Mover el item del Project v2 a "In Progress"

## 1. Fase 0 — Completar system_config (additivo, configuracion-club)

- [ ] 1.1 Migración `V8__add_pricing_to_system_config.sql`: `ADD price_per_hour NUMERIC(12,2) DEFAULT 15.00` + `CHECK (price_per_hour > 0)`; `ADD cancellation_deadline_hours INTEGER NOT NULL DEFAULT 2` + `CHECK (cancellation_deadline_hours >= 0)`
- [ ] 1.2 Entidad `SystemConfig`: añadir campos `pricePerHour` (BigDecimal) y `cancellationDeadlineHours` (Integer) + getters/setters/builder
- [ ] 1.3 `SystemConfigResponse`: exponer ambos campos
- [ ] 1.4 `UpdateSystemConfigRequest`: aceptar ambos campos con validación (`@Positive`, `@PositiveOrZero`)
- [ ] 1.5 `SystemConfigService`: mapeo y persistencia de los nuevos campos
- [ ] 1.6 Tests de `GET`/`PATCH` config con los campos nuevos y validaciones de borde

## 2. Esquema de datos — payments e idempotencia

- [ ] 2.1 Migración `V9__create_payments_and_idempotency.sql`: enums `payment_method`/`payment_status`/`payment_gateway`; tabla `payments` (data-model §3.5) con FK UNIQUE a `reservations`, `chk_pay_amount`, `chk_pay_cash_admin` e índices
- [ ] 2.2 En la misma migración: tabla `idempotency_keys(key, user_id, reservation_id, created_at)` con `UNIQUE(user_id, key)` e índice por `created_at`
- [ ] 2.3 Entidad `Payment` + enums de dominio `PaymentMethod`/`PaymentStatus`/`PaymentGateway`
- [ ] 2.4 Entidad/repositorio `IdempotencyKey`
- [ ] 2.5 Repositorios JPA `PaymentJpaRepository` e `IdempotencyKeyJpaRepository`

## 3. Dominio de escritura de reservas

- [ ] 3.1 Puertos de salida de escritura (`ReservationCommandPort`, `PaymentCommandPort`)
- [ ] 3.2 Máquina de estados de reserva (transiciones válidas; rechazo de inválidas con 422)
- [ ] 3.3 Lógica de cálculo de precio: `amount = price_per_hour × duration_minutes / 60` (congelado)
- [ ] 3.4 Política de cancelación basada en `cancellation_deadline_hours` (con/sin reembolso)

## 4. Crear reserva (POST /api/reservas)

- [ ] 4.1 `CrearReservaService`: validación de entrada (fecha futura, duración ∈ {60,90,120,150,180}, startTime en :00/:30, límite de participantes)
- [ ] 4.2 Idempotencia: consulta `(user_id, Idempotency-Key)` → si existe, devolver reserva existente sin duplicar
- [ ] 4.3 Creación atómica: reserva `PENDING_CONFIRMATION` + participantes (owner slot 1) + `payments` PENDING en una transacción
- [ ] 4.4 Traducción de la violación del constraint `excl_res_no_overlap` a `409 CONFLICT`
- [ ] 4.5 Invalidar caché de disponibilidad para la fecha afectada al commit
- [ ] 4.6 `ReservaController` POST + DTOs de request/response (priceTotal desde backend)

## 5. Cancelar y gestión

- [ ] 5.1 `CancelarReservaService`: autorización (owner o ADMIN, RN-AUTH-02), aplicar política de plazo, marcar pago `REFUNDED` si procede
- [ ] 5.2 `DELETE /api/reservas/{id}`: 204 / 403 / 422 según caso
- [ ] 5.3 `AdminReservaService` + `PATCH /api/admin/reservas/{id}/estado` (bypass de política, transiciones válidas)
- [ ] 5.4 Invalidar caché de disponibilidad al cancelar

## 6. Consulta

- [ ] 6.1 `GET /api/reservas`: solo reservas donde el usuario es owner o participante (RN-AUTH-01), con `JOIN FETCH payments` (evitar N+1)
- [ ] 6.2 `GET /api/reservas/{id}`: 403 (no 404) para no-owner/no-participante (prevención BOLA)
- [ ] 6.3 `GET /api/admin/reservas`: listado completo (solo ADMIN)

## 7. API spec

- [ ] 7.1 Actualizar `docs/openapi.yaml`: header `Idempotency-Key`, esquemas de `payments` y respuestas 201/400/403/409/422 de reservas

## 8. Testing

- [ ] 8.1 Tests unitarios de cálculo de precio, máquina de estados y política de cancelación
- [ ] 8.2 Tests de integración de creación atómica (reserva + participantes + pago) con Testcontainers PostgreSQL
- [ ] 8.3 Test de concurrencia: dos `POST` simultáneos a la misma franja → exactamente un 201 y un 409 (constraint gist)
- [ ] 8.4 Tests de idempotencia: misma key → mismo recurso, sin duplicado; key nueva → nueva reserva
- [ ] 8.5 Tests de autorización: BOLA en `GET /{id}`, cancelación por no-owner (403), listado admin por USER (403)
- [ ] 8.6 Test US-024: cambiar `price_per_hour` no altera el `amount` de reservas existentes
- [ ] 8.7 Cobertura dentro del umbral del proyecto (JaCoCo)

## 9. Cierre

- [ ] 9.1 `verification-specialist`: build + tests + probes (PASS/FAIL)
- [ ] 9.2 `reality-checker`: user journey reservar→confirmar(admin)→cancelar end-to-end
- [ ] 9.3 Commit y push a la rama (reservado al orquestador)
- [ ] 9.4 Crear PR con `Closes #<id>` y validar CI
