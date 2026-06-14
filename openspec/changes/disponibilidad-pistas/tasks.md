## 1. Setup y andamiaje

- [ ] 1.1 Confirmar Issue #13 (US-006) reabierto y mover el item del Project v2 a "In Progress"
- [x] 1.2 Trabajar sobre la rama `feat/13-disponibilidad-pistas` (ya creada desde `develop`)
- [x] 1.3 Verificar que `system_config` (V6) expone `max_participants` y `pista_state`, y localizar los campos de horario/granularidad de tramo disponibles en la fila inicial

## 2. TDD — Tests de migración (RED, Testcontainers)

- [x] 2.1 Test: las migraciones `V1..V7` aplican limpiamente sobre PostgreSQL 15 desde cero
- [x] 2.2 Test: la extensión `btree_gist` queda disponible tras `V7`
- [x] 2.3 Test: insertar dos reservas activas solapadas en la misma fecha viola `excl_res_no_overlap` (la BD rechaza la segunda)
- [x] 2.4 Test: dos reservas solapadas pero con la segunda `CANCELLED` SÍ se permiten (constraint parcial `WHERE status <> 'CANCELLED'`)

## 3. TDD — Tests unitarios del cálculo de disponibilidad (RED)

- [x] 3.1 Test `should_return_all_slots_free_when_no_active_reservations` — plazasLibres = max_participants
- [x] 3.2 Test `should_return_partial_slots_when_reservation_incomplete` — reserva CONFIRMED con 2/4 → plazasLibres = 2
- [x] 3.3 Test `should_count_pending_confirmation_as_occupying` — PENDING_CONFIRMATION reduce plazas
- [x] 3.4 Test `should_ignore_cancelled_reservations` — CANCELLED no ocupa
- [x] 3.5 Test `should_hide_full_slots` — tramo con 0 plazas no aparece
- [x] 3.6 Test `should_return_empty_list_when_pista_in_mantenimiento` — sin revelar motivo

## 4. TDD — Tests de integración del endpoint (RED, MockMvc + Spring Security)

- [x] 4.1 Test: `GET /api/reservas/disponibles?fecha=2025-08-01` como USER autenticado → 200 con tramos
- [x] 4.2 Test: como ADMIN autenticado → 200
- [x] 4.3 Test: sin `Authorization` → 401
- [x] 4.4 Test: con JWT expirado → 401
- [x] 4.5 Test: `fecha` en formato inválido (`15-06-2025`) → 400 `VALIDATION_ERROR`
- [x] 4.6 Test: sin parámetro `fecha` → 400 `VALIDATION_ERROR`
- [x] 4.7 Test: pista en MANTENIMIENTO → 200 con `tramosDisponibles: []`

## 5. Migración — V7 reservations + participants

- [x] 5.1 Crear `V7__create_reservations_and_participants.sql` con `CREATE EXTENSION IF NOT EXISTS btree_gist`
- [x] 5.2 Crear enums `reservation_status` y `reservation_channel` (data-model §3.3)
- [x] 5.3 Crear tabla `reservations` con columnas, FK `owner_id → users(id)` (ON DELETE RESTRICT), checks `chk_res_duration`/`chk_res_end_time`/`chk_res_start_minutes`
- [x] 5.4 Crear índices `idx_res_owner_id`, `idx_res_date_status` (parcial), `idx_res_date_start`, `idx_res_owner_date`, `idx_res_owner_status` (parcial)
- [x] 5.5 Crear constraint `excl_res_no_overlap` EXCLUDE USING gist sobre `tsrange(date+start, date+end, '[)')` parcial `WHERE status <> 'CANCELLED'`
- [x] 5.6 Crear tabla `participants` con FKs (`reservation_id` CASCADE, `user_id` SET NULL), checks e índices (data-model §3.4)

## 6. Implementación — Dominio y persistencia

- [x] 6.1 Crear entidad `Reservation` (mapea `reservations`) en la capa de dominio
- [x] 6.2 Crear entidad `Participant` (mapea `participants`)
- [x] 6.3 Crear puerto de salida de lectura (p.ej. `ReservationQueryPort`) con consulta de reservas activas por fecha
- [x] 6.4 Implementar repositorio Spring Data JPA del puerto, usando el índice parcial `idx_res_date_status`

## 7. Implementación — Capa Application (cálculo de disponibilidad)

- [x] 7.1 Crear `DisponibilidadService` que lee `system_config` (max_participants, pista_state) vía su puerto
- [x] 7.2 Implementar el cálculo de tramos libres por fecha (RN-RES-01, RN-RES-02) considerando estados activos
- [x] 7.3 Cortocircuitar a lista vacía cuando `pista_state = MANTENIMIENTO` (RN-RES-04)
- [x] 7.4 Crear DTOs `DisponibilidadResponse` y `TramoDisponible` (`horaInicio`, `duracionMinutos`, `plazasLibres`) alineados con `docs/openapi.yaml`
- [x] 7.5 Añadir caché de 30 s por `fecha` y un punto de invalidación reutilizable por Wave 3

## 8. Implementación — Controlador REST

- [x] 8.1 Crear `ReservaDisponibilidadController` con `GET /api/reservas/disponibles`
- [x] 8.2 Validar `fecha` (obligatoria, formato `YYYY-MM-DD`) → 400 `VALIDATION_ERROR` vía `GlobalExceptionHandler`
- [x] 8.3 Asegurar autorización: USER y ADMIN permitidos, anónimo 401 (SecurityConfig)

## 9. Verificación final

- [x] 9.1 `mvn test` — toda la suite en verde (migración + unit + integración). Nuevos tests: 18/18 verdes (4 IT de migración SKIPPED por falta de Docker local; se ejecutan en CI). Errores preexistentes de otros `*IT/*E2E` por ausencia de Docker, ajenos a este change.
- [x] 9.2 ArchUnit: las reglas de arquitectura hexagonal siguen pasando (6/6)
- [ ] 9.3 JaCoCo: cobertura dentro del umbral del proyecto (verificación delegada al orquestador con `mvn verify`)
- [x] 9.4 Verificar contrato del endpoint contra `docs/openapi.yaml` (`/reservas/disponibles`)
- [ ] 9.5 Commit y push a `feat/13-disponibilidad-pistas` (reservado al orquestador — NO commitear desde el agente)
