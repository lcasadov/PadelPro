## Why

La suite E2E (`e2e/`, Playwright) tiene un único smoke (`login → crear reserva`, change `e2e-smoke-tests` #206). Cubre el journey crítico, pero deja sin red de seguridad end-to-end otros flujos reales (cancelar reserva, dashboard admin, vinculación Telegram, login inválido, partidas, navegación). Ampliar la cobertura E2E reduce el riesgo de regresiones que los tests de componente con mocks no ven (la clase de bug de #201).

## What Changes

- Se añaden nuevos specs Playwright en `e2e/tests/`, uno por journey, contra el **stack real** (sin mocks), re-ejecutables (patrón de reintento sobre `409`) y deterministas:
  - `login-invalido`: credenciales incorrectas → mensaje genérico (401, anti-enumeración).
  - `mis-reservas-cancelar`: crear reserva → Mis Reservas → cancelar → verificar.
  - `dashboard-admin`: ocupación + ingresos cargan; export CSV descarga.
  - `navegacion-home`: control "Inicio" del flujo de reservas vuelve a Home.
  - `vincular-telegram`: pantalla de vinculación muestra el código/instrucciones (sin bot real).
  - `partidas-listado`: la página de Partidas carga (listado o estado vacío).
- Todos entran en el job `e2e` de CI ya existente (sin cambios de workflow).
- **Fix de bug revelado por el E2E (administracion-club, #217):** el nuevo `dashboard-admin` destapó que `GET /api/admin/dashboard/{ocupacion,ingresos,exportar}` devolvían 500 (enmascarado como 401) contra Postgres real — las queries JPQL usaban literales de enum que Hibernate casteaba a un tipo enum de Postgres inexistente (`'CANCELLED'::ReservationStatus`). Se corrige usando parámetros vinculados (patrón ya existente en el repo) y se añade un **IT de regresión** (`DashboardServiceIT`, Postgres real) que ejecuta el SQL — el hueco que dejó pasar el bug (los unit mockeaban el repositorio). Con el fix, `dashboard-admin` cubre el journey completo (métricas + descarga CSV).

## Capabilities

### New Capabilities
<!-- Ninguna: amplía la cobertura E2E de la capability transversal ci-cd-deploy (requisito "suite E2E smoke" ya existente). -->

### Modified Capabilities
<!-- Sin cambios de requisitos: el requisito de suite E2E ya existe en ci-cd-deploy (change e2e-smoke-tests). Este change amplía la cobertura; es implementación de tests. -->

## Impact

- **Testing (`e2e/`):** nuevos `*.spec.ts`. Sin cambios de código de producto ni de esquema. El job `e2e` de CI corre la suite ampliada (algo más de tiempo).
- **Fase del producto:** fase-1 (calidad transversal).

## Fuera de alcance

- **Partidas → "unirse" cross-usuario:** requiere una partida abierta de OTRO usuario y solo hay el admin de bootstrap; se cubre el **listado** de partidas, no la acción de unirse (documentado). Sembrar un segundo socio activo queda como follow-up.
- Cobertura E2E de pagos Redsys en vivo (requiere TPV) y del webhook de Telegram con bot real.
- No sustituye los tests de componente (vitest + MSW); los complementa.
