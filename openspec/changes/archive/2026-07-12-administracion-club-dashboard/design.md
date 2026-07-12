## Context

El spec `administracion-club` define métricas de ocupación e ingresos y export CSV, situando los endpoints dedicados `/api/admin/dashboard/*` en "Fase 2" (v1.0 preveía agregación en frontend sobre `GET /api/admin/reservas` y `GET /api/admin/pagos`). Se opta por implementar los **endpoints dedicados** por robustez, testabilidad y para cumplir RN-RGPD-04 (agregados sin PII) desde el backend. Fuentes: `reservations`, `payments`, `system_config` (horario de apertura + `price_per_hour`). El prefijo `/api/admin/**` ya exige `ROLE_ADMIN`.

## Goals / Non-Goals

**Goals:**
- Ocupación % por rango (RN-ADM-02), ingresos por rango con desglose por método (RN-ADM-03), export CSV (RN-ADM-04).
- Solo ADMIN (RN-ADM-01); agregados sin PII directa (RN-RGPD-04).
- Frontend admin usable con selector de rango y export.

**Non-Goals:**
- Almacén analítico, gráficas avanzadas, tiempo real, formatos != CSV.

## Decisions

### D1 — Endpoints backend dedicados con agregación en BD (frente a agregar en frontend)
Se implementan `/api/admin/dashboard/{ocupacion,ingresos,exportar}` que agregan en el backend (consultas SUM/COUNT sobre `payments`/`reservations`). Ventajas frente a la agregación-en-frontend de v1.0: menos datos por la red, cálculo consistente, testable con unit tests, y control de RN-RGPD-04 (el backend devuelve solo agregados). El spec lo contemplaba como Fase 2; se adelanta.

### D2 — Cálculo de ocupación (RN-ADM-02)
`ocupación% = (slots reservados no cancelados en [inicio,fin]) / (slots disponibles en [inicio,fin]) × 100`. Los slots disponibles se derivan del horario de apertura de `system_config` (hora inicio/fin, tamaño de slot) por cada día del rango. Las reservas no canceladas cuentan sus slots (duración/tamaño de slot). Se documentan los supuestos (una pista; slots de 30 min o el tamaño configurado). Rango vacío o sin horario → 0% sin error.

### D3 — Cálculo de ingresos (RN-ADM-03)
`SUM(payments.amount) WHERE status='PAID' AND paid_at ∈ [inicio,fin]`, agrupado por `method` (REDSYS/CASH). Se devuelve total + desglose. Fechas inclusivas; se define claramente el tratamiento de la zona horaria (usar el mismo criterio que el resto del backend).

### D4 — Export CSV (RN-ADM-04, RN-RGPD-04)
`GET /exportar` devuelve `text/csv` (con `Content-Disposition: attachment`) del informe de uso del período: filas agregadas por día (fecha, reservas, ocupación%, ingresos, desglose método), **sin nombres ni datos personales** — solo agregados/ids. Mismas restricciones ADMIN.

### D5 — Frontend
Página `AdminDashboardPage` bajo `AdminRoute`. Selector de rango (fechaInicio/fechaFin, con default p. ej. mes actual). Llama a los 3 endpoints; muestra tarjetas de ocupación e ingresos (desglose REDSYS/CASH) y un botón "Exportar CSV" que descarga el fichero. Reutiliza `httpClient` (`/api`, mismo origen). Enlace desde el panel de administración existente.

## Risks / Trade-offs

- **[Definición de "slot disponible" ambigua]** → Mitigación: derivar del horario de `system_config`; documentar supuestos (una pista, tamaño de slot). Si falta config, 0% sin romper. Tests cubren rango con/sin reservas y sin config.
- **[Rendimiento en rangos grandes]** → Mitigación: agregación en BD (SUM/COUNT con índices por fecha/estado), no traer filas al frontend. Rangos acotados por el selector.
- **[Zona horaria de `paid_at`/`reservation_date`]** → Mitigación: usar el criterio existente del backend; tests con fechas límite del rango.
- **[Fuga de PII en el CSV]** (RN-RGPD-04) → Mitigación: el CSV solo lleva agregados por día; test que verifica ausencia de nombres/emails.

## Migration Plan

1. Backend: paquete dashboard (controller + servicio + DTOs + queries). Sin migración de esquema.
2. Frontend: página + ruta + cliente + enlace.
3. Rollback: retirar el paquete/página; no afecta a datos ni a otros endpoints (aditivo).
