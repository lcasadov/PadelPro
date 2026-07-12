## Why

`administracion-club` es la última capability de producto pendiente: el ADMIN no tiene una visión agregada del rendimiento del club (ocupación de pistas, ingresos por período). Los datos ya existen (`reservations`, `payments`, `system_config`) pero no hay ni agregación ni panel. Se implementa el dashboard para cerrar la funcionalidad de administración.

## What Changes

- **Backend — endpoints de dashboard** bajo `/api/admin/dashboard/*` (solo ADMIN, ya cubierto por el filtro `/api/admin/**`, RN-ADM-01):
  - `GET /ocupacion?fechaInicio&fechaFin` → % de ocupación = `slots reservados (status != CANCELLED) / slots disponibles del período` × 100 (RN-ADM-02); los slots disponibles se derivan del horario de apertura de `system_config`.
  - `GET /ingresos?fechaInicio&fechaFin` → suma de `payments.amount` con `status=PAID` y `paid_at` en rango, con **desglose por método** (REDSYS / CASH) (RN-ADM-03).
  - `GET /exportar?fechaInicio&fechaFin` → **CSV** del informe de uso del período (mismas restricciones ADMIN, RN-ADM-04; sin datos personales directos, RN-RGPD-04).
- **Frontend — página de dashboard admin** (ruta protegida `AdminRoute`): selector de rango de fechas, tarjetas de ocupación e ingresos (con desglose por método) y botón de exportar CSV.

## Capabilities

### New Capabilities
<!-- Ninguna nueva: se implementa la capability administracion-club ya especificada (los endpoints dedicados que el spec situaba en "Fase 2"). -->

### Modified Capabilities
<!-- Sin cambios de requisitos: el spec de administracion-club ya define ocupación/ingresos/export (Requirements 1–3). Change de implementación. -->

## Impact

- **Backend:** nuevo módulo/paquete de dashboard (controller `/api/admin/dashboard/*`, servicio de agregación, DTOs). Lee `reservations` (ocupación), `payments` (ingresos) y `system_config` (horario para el total de slots). Sin cambios de esquema.
- **Frontend:** nueva página + ruta admin + cliente API; enlace desde el panel admin.
- **Fase del producto:** cierra `administracion-club` (el spec la situaba en Fase 2; se implementan los endpoints dedicados en lugar de la agregación-en-frontend de v1.0, por robustez y RN-RGPD-04).

## Fuera de alcance

- Gráficas avanzadas / históricos de largo plazo con almacén analítico dedicado.
- Métricas en tiempo real o push; el dashboard es consulta bajo demanda por rango.
- Exportar formatos distintos de CSV (PDF/Excel).
