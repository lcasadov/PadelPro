## 1. Specs de journeys

- [x] 1.1 `login-invalido.spec.ts` — password incorrecta → "Credenciales inválidas", no navega a /home
- [x] 1.2 `mis-reservas-cancelar.spec.ts` — crear reserva → Mis Reservas → cancelar → verificar (autocontenido)
- [x] 1.3 `dashboard-admin.spec.ts` — journey COMPLETO: métricas cargan (€/%) + export CSV dispara descarga (`waitForEvent('download')`)
- [x] 1.4 `navegacion-home.spec.ts` — control "Inicio" del flujo de reservas vuelve a /home
- [x] 1.5 `vincular-telegram.spec.ts` — pantalla de vinculación muestra código/instrucciones (sin bot real)
- [x] 1.6 `partidas-listado.spec.ts` — la página de Partidas carga (listado o estado vacío); "unirse" cross-usuario documentado como fuera de alcance
- [x] 1.7 `helpers.ts` — `login()` resiliente al rate-limit (retry sobre 429), `mananaISO()`, `crearReservaDesdeHome()` (reintento sobre 409)

## 2. Fix revelado por el E2E (administracion-club #217)

- [x] 2.1 `dashboard-admin` destapó 500 (disfrazado de 401) en `/api/admin/dashboard/**`: queries JPQL con literal de enum → cast a tipo enum Postgres inexistente
- [x] 2.2 Fix: parámetro vinculado (`:cancelled`/`:paid`) en las 4 queries + `DashboardService` pasa `ReservationStatus.CANCELLED`/`PaymentStatus.PAID`
- [x] 2.3 `DashboardServiceIT` (Postgres real) que ejecuta el SQL — regresión que habría cazado el bug (los unit mockeaban el repo)

## 3. Verificación y cierre

- [x] 3.1 Los 7 specs en verde contra el stack real (local): `7 passed`
- [x] 3.2 Backend unit en verde; el IT del dashboard corre en el job build-and-test de CI (Docker real)
- [x] 3.3 PR
