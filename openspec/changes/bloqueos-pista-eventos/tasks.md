# Tareas — bloqueos-pista-eventos

## 1. Backend — modelo y persistencia

- [ ] 1.1 Migración Flyway **V19** `bloqueo_pista`: `id` PK, `fecha DATE`, `hora TIME`, `motivo TEXT`, `created_by_user_id BIGINT`, `created_at TIMESTAMPTZ`, `UNIQUE(fecha,hora)`, índice por `fecha`.
- [ ] 1.2 Entidad `BloqueoPista` + repositorio JPA (`findByFecha`, `existsByFechaAndHora`, `findHorasByFecha`).
- [ ] 1.3 Puerto `BloqueoQueryPort.findHorasBloqueadasByFecha(fecha): Set<LocalTime>` + adaptador.

## 2. Backend — servicio de bloqueos

- [ ] 2.1 `BloqueoService.crear(fecha, horas, motivo, adminId)`: reusa `ReservationQueryPort.findActiveOccupanciesByDate`; si alguna hora solapa una reserva activa → excepción de conflicto (409/422) con las franjas y reservas en conflicto (todo-o-nada). Inserta las horas que falten (idempotente sobre `UNIQUE`).
- [ ] 2.2 `listar(fecha)` y `eliminar(id)`.
- [ ] 2.3 Invalidar caché `available-slots` de la fecha al crear y al borrar (`DisponibilidadCacheInvalidator.invalidate`).
- [ ] 2.4 Auditoría `BLOQUEO_CREATED` / `BLOQUEO_DELETED` (patrón existente).

## 3. Backend — endpoints e integración en disponibilidad

- [ ] 3.1 `POST /api/admin/bloqueos`, `GET /api/admin/bloqueos?fecha=`, `DELETE /api/admin/bloqueos/{id}` — todos `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] 3.2 `DisponibilidadService`: inyectar `BloqueoQueryPort`; omitir del bucle de tramos las horas bloqueadas de la fecha (sin revelar motivo). Ajustar el `DisponibilidadConfig` (bean).

## 4. Frontend

- [ ] 4.1 `src/services/bloqueosApi.ts`: `getBloqueos(fecha)`, `crearBloqueos(fecha, horas, motivo)`, `eliminarBloqueo(id)` sobre el `httpClient` compartido; mapear el error de conflicto a un tipo utilizable en la UI.
- [ ] 4.2 Página admin de bloqueos: elegir fecha → rejilla horaria 8–23 con estado por franja (libre/reservada/bloqueada); marcar libres → bloquear con motivo; desbloquear; aviso inline ante conflicto. Ruta protegida ADMIN + enlace en Home admin.

## 5. Testing

- [ ] 5.1 Unit backend: `BloqueoService` (crear OK, conflicto rechaza todo, idempotencia, borrar); `DisponibilidadService` excluye franjas bloqueadas.
- [ ] 5.2 IT: persistencia `bloqueo_pista` + disponibilidad excluye bloqueadas (Testcontainers).
- [ ] 5.3 Componente frontend (Vitest + MSW): rejilla, bloquear, conflicto, desbloquear.
- [ ] 5.4 E2E (`bloqueos-pista.spec.ts`): ADMIN bloquea una hora → el jugador ya no la ve en disponibilidad; ADMIN la desbloquea → reaparece.

## 6. QA y cierre

- [ ] 6.1 Backend `mvn -DskipITs test` verde; Frontend `tsc`+`lint`+`test`+`build` verde; E2E verde contra el stack real.
- [ ] 6.2 PR y merge tras CI verde; archivar el change (con sync de specs: crea capability `bloqueos-pista` y modifica `disponibilidad-pistas`).
