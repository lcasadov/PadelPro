## 1. Backend — ocupación

- [x] 1.1 Servicio de agregación de ocupación: slots reservados (reservas `status != CANCELLED` en [inicio,fin]) / slots disponibles (derivados del horario de `system_config`) × 100 (RN-ADM-02)
- [x] 1.2 `GET /api/admin/dashboard/ocupacion?fechaInicio&fechaFin` → DTO con % y contadores; rango vacío/sin horario → 0% sin error

## 2. Backend — ingresos

- [x] 2.1 Agregación de ingresos: `SUM(payments.amount)` `status=PAID` con `paid_at` en rango, desglose por `method` (REDSYS/CASH) (RN-ADM-03)
- [x] 2.2 `GET /api/admin/dashboard/ingresos?fechaInicio&fechaFin` → DTO con total + desglose por método

## 3. Backend — export CSV

- [x] 3.1 `GET /api/admin/dashboard/exportar?fechaInicio&fechaFin` → `text/csv` (Content-Disposition attachment), informe por día (fecha, reservas, ocupación%, ingresos, desglose); SIN datos personales (RN-RGPD-04)

## 4. Backend — seguridad y tests

- [x] 4.1 Verificar que `/api/admin/dashboard/**` exige ROLE_ADMIN (RN-ADM-01); un USER recibe 403
- [x] 4.2 Tests unitarios (≥80% código nuevo): ocupación (con/sin reservas, sin config→0%), ingresos (desglose, rango sin pagos→0), CSV (contenido + sin PII), 403 para USER

## 5. Frontend — dashboard

- [x] 5.1 `AdminDashboardPage` bajo `AdminRoute` (solo ADMIN): selector de rango (default mes actual)
- [x] 5.2 Cliente API (`dashboardApi`) para ocupación/ingresos/exportar (usa `httpClient`)
- [x] 5.3 Tarjetas de ocupación e ingresos (desglose REDSYS/CASH) + botón "Exportar CSV" (descarga)
- [x] 5.4 Enlace desde el panel de administración; tests (vitest+MSW) de la página y del cliente (≥80% código nuevo)

## 6. QA y cierre

- [x] 6.1 Backend suite unitaria verde (14 tests dashboard + resto); Frontend `tsc`+`lint`+`test`(210)+`build` verde
- [x] 6.2 Contrato front↔back alineado (mismo contrato explícito: `OcupacionResponse{slotsReservados,slotsDisponibles,ocupacionPct}`, `IngresosResponse{total,porMetodo{REDSYS,CASH}}`, CSV)
- [x] 6.3 `openspec/plan.md` actualizado (administracion-club ✅) + PR

## Notas de implementación
- Endpoints `/api/admin/dashboard/{ocupacion,ingresos,exportar}` (backend agrega en BD) + `AdminDashboardPage` (frontend). Solo ADMIN (`/api/admin/**` + `@PreAuthorize` defensivo; test USER→403).
- Supuestos de ocupación (documentados): defaults de `DisponibilidadService` (1 pista, 08:00–23:00, slots 60min → 15/día); sin fila `system_config` → 0% (sin división por cero). `paid_at` con `ZoneId.systemDefault()`, `fechaFin` inclusiva. CSV RFC 4180 sin PII (RN-RGPD-04).
- El contrato concreto usa nombres propios (`slotsReservados`/`ocupacionPct`/`total`), no los ilustrativos del spec Fase 2; front y back consistentes entre sí y cumplen los Requirements 1–3.
- Límite de 12 meses del rango (caso límite del spec) NO implementado — follow-up opcional.
