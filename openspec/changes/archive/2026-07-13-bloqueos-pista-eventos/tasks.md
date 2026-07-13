# Tareas — bloqueos-pista-eventos

## 1. Backend — modelo y persistencia

- [x] 1.1 Migración Flyway **V19** `bloqueo_pista`: `id` PK, `fecha DATE`, `hora TIME`, `motivo TEXT`, `created_by_user_id BIGINT`, `created_at TIMESTAMPTZ`, `UNIQUE(fecha,hora)`, índice por `fecha`.
- [x] 1.2 Entidad `BloqueoPista` + repositorio JPA (`findByFecha`, `existsByFechaAndHora`, `findHorasByFecha`).
- [x] 1.3 Puerto `BloqueoQueryPort.findHorasBloqueadasByFecha(fecha): Set<LocalTime>` + adaptador.

## 2. Backend — servicio de bloqueos

- [x] 2.1 `BloqueoService.crear(fecha, horas, motivo, adminId)`: reusa `ReservationQueryPort.findActiveOccupanciesByDate`; si alguna hora solapa una reserva activa → excepción de conflicto (409/422) con las franjas y reservas en conflicto (todo-o-nada). Inserta las horas que falten (idempotente sobre `UNIQUE`).
- [x] 2.2 `listar(fecha)` y `eliminar(id)`.
- [x] 2.3 Invalidar caché `available-slots` de la fecha al crear y al borrar (`DisponibilidadCacheInvalidator.invalidate`).
- [x] 2.4 Auditoría `BLOQUEO_CREATED` / `BLOQUEO_DELETED` (patrón existente).

## 3. Backend — endpoints e integración en disponibilidad

- [x] 3.1 `POST /api/admin/bloqueos`, `GET /api/admin/bloqueos?fecha=`, `DELETE /api/admin/bloqueos/{id}` — todos `@PreAuthorize("hasRole('ADMIN')")`.
- [x] 3.2 `DisponibilidadService`: inyectar `BloqueoQueryPort`; omitir del bucle de tramos las horas bloqueadas de la fecha (sin revelar motivo). Ajustar el `DisponibilidadConfig` (bean).

## 4. Frontend

- [x] 4.1 `src/services/bloqueosApi.ts`: `getBloqueos(fecha)`, `crearBloqueos(fecha, horas, motivo)`, `eliminarBloqueo(id)` sobre el `httpClient`; conflicto 409 → `ReservaApiError` (code CONFLICT + details) usable en la UI.
- [x] 4.2 `BloqueosPage` (`/admin/bloqueos`): selector de fecha → rejilla 8:00–22:00 con estado por franja (LIBRE marcable / OCUPADA / BLOQUEADA con desbloquear); marcar libres → bloquear con motivo; aviso inline ante conflicto. Ruta bajo `AdminRoute` + enlace "Bloquear franjas" en Home admin. Combina `GET /reservas/disponibles` + `GET /admin/bloqueos`.

## 5. Testing

- [x] 5.1 Unit backend: `BloqueoService` (crear OK, conflicto rechaza todo, idempotencia, borrar) — 5/5; `DisponibilidadService` excluye franjas bloqueadas — test añadido, 9/9.
- [ ] 5.2 IT: persistencia `bloqueo_pista` + disponibilidad excluye bloqueadas (Testcontainers).
- [x] 5.3 Componente frontend (Vitest + MSW) — 5/5: rejilla con estados, bloquear con motivo (POST), conflicto 409 avisa, desbloquear (DELETE) refresca, exige motivo.
- [x] 5.4 E2E (`bloqueos-pista.spec.ts`) verde: ADMIN bloquea una franja → desaparece de la disponibilidad del jugador → desbloquea → reaparece.

## 6. QA y cierre

- [x] 6.1 Backend unit verde (BloqueoService 5/5, DisponibilidadService 9/9; suite unitaria completa 417/0); Frontend `tsc`+`lint`+`test`(5/5 componente) verde; suite E2E completa **15/15** contra el stack real. IT Testcontainers difieren a CI Linux (Docker 29 ambiental).
- [ ] 6.2 PR y merge tras CI verde; archivar el change (con sync de specs: crea capability `bloqueos-pista` y modifica `disponibilidad-pistas`).
