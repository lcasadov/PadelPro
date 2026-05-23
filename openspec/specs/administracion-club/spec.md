# Capability: `administracion-club`

## Resumen
Panel de administración del club con métricas de ocupación de pistas, ingresos por período
y exportación de informes de uso. Proporciona al ADMIN una visión agregada del rendimiento
del club para la toma de decisiones operativas. En v1.0, las métricas se obtienen desde el
frontend aggregando datos de las capabilities `reservas` y `pagos-redsys` a través de los
endpoints existentes. En Fase 2 se expondrán como endpoints REST dedicados bajo
`/api/admin/dashboard/*`.

## Fase
🔵 Fase 2

> **Nota de implementación v1.0:** En v1.0, esta capability no tiene backend REST propio.
> El frontend construye las métricas de ocupación e ingresos consultando:
> - `GET /api/admin/reservas?fecha=...&status=...` (paginado) para calcular ocupación.
> - `GET /api/admin/pagos?status=PAID` (paginado) para calcular ingresos.
> Esta aproximación tiene limitaciones de rendimiento para períodos largos. En Fase 2 se
> expondrán como endpoints REST `/api/admin/dashboard/*` con agregaciones precalculadas en
> backend.

## Reglas de negocio implicadas
- **RN-ADM-01**: Solo el rol ADMIN puede acceder a las métricas del dashboard; un USER nunca debe ver datos agregados de todos los usuarios.
- **RN-ADM-02**: El porcentaje de ocupación se calcula como `(slots reservados con status != CANCELLED) / (total slots disponibles en el período)` × 100; los slots disponibles se derivan del horario de apertura configurado en `system_config`.
- **RN-ADM-03**: Los ingresos del período se calculan como la suma de `payments.amount` donde `payments.status = 'PAID'` y `payments.paid_at` está dentro del rango de fechas solicitado.
- **RN-ADM-04**: La exportación CSV de informes de uso debe respetar las mismas restricciones de acceso que la visualización en pantalla (solo ADMIN).
- **RN-RGPD-04**: Los informes exportados no deben incluir datos de identificación personal directa en campos de resumen (nombres completos en exportaciones de uso agregado); solo IDs anonimizables o datos agregados.

## Entidades implicadas
- **`reservations`**: fuente de datos de ocupación. Campos relevantes: `reservation_date`, `start_time`, `end_time`, `duration_minutes`, `status`, `owner_id`, `channel`.
- **`payments`**: fuente de datos de ingresos. Campos relevantes: `amount`, `status`, `paid_at`, `method`, `gateway`.
- **`users`**: metadatos de jugadores frecuentes (Fase 2). Solo `id` y `login` para identificación sin exposición de datos personales en informes.
- **`system_config`**: `price_per_hour` y `cancellation_deadline_hours` como parámetros de referencia para calcular ingresos esperados vs. reales.

## Endpoints
En v1.0: Sin endpoint REST directo para dashboard — aggregación en frontend sobre endpoints existentes.

En Fase 2 (target behavior):
- `GET /api/admin/dashboard/ocupacion` — métricas de ocupación por período (% slots utilizados vs disponibles).
- `GET /api/admin/dashboard/ingresos` — suma de pagos PAID por período con desglose por método (REDSYS / CASH).
- `GET /api/admin/dashboard/exportar` — exportación CSV del informe de uso.

## Permisos
| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Consultar métricas de ocupación | ✅ | ❌ | ❌ |
| Consultar métricas de ingresos | ✅ | ❌ | ❌ |
| Exportar informe CSV | ✅ | ❌ | ❌ |
| Ver jugadores frecuentes | ✅ | ❌ | ❌ |

## Requirements

### Requirement 1: Consulta de ocupación de pistas por período
**El sistema DEBE permitir al ADMIN consultar el porcentaje de ocupación de pistas para un rango de fechas, calculado como la proporción de slots de tiempo utilizados (reservas no canceladas) sobre el total de slots disponibles en el período.**

#### Scenario 1: ADMIN consulta ocupación de la semana actual
- **GIVEN** un ADMIN autenticado Y existen reservas con distintos estados en la semana en curso
- **WHEN** el ADMIN invoca `GET /api/admin/dashboard/ocupacion?fechaInicio=2025-06-16&fechaFin=2025-06-22` (Fase 2)
- **THEN** la respuesta incluye: `totalSlotsDisponibles` (todos los tramos horarios posibles del período según horario de apertura), `slotsOcupados` (reservas con `status != 'CANCELLED'`), `porcentajeOcupacion` (ratio como número entre 0 y 100 con 2 decimales), Y el cálculo excluye reservas en estado `CANCELLED`

#### Scenario 2: USER intenta acceder a métricas de ocupación — 403
- **GIVEN** un usuario con rol USER autenticado
- **WHEN** intenta invocar `GET /api/admin/dashboard/ocupacion` (Fase 2)
- **THEN** el sistema devuelve 403 Forbidden, Y no se expone ningún dato de reservas en la respuesta de error

### Requirement 2: Consulta de ingresos por período
**El sistema DEBE permitir al ADMIN consultar el total de ingresos para un rango de fechas, con desglose por método de pago (REDSYS, CASH), basándose en pagos con `status=PAID` cuyo `paid_at` cae dentro del rango.**

#### Scenario 3: ADMIN consulta ingresos del mes anterior
- **GIVEN** un ADMIN autenticado Y existen pagos en estado `PAID` en el mes anterior
- **WHEN** el ADMIN invoca `GET /api/admin/dashboard/ingresos?fechaInicio=2025-05-01&fechaFin=2025-05-31` (Fase 2)
- **THEN** la respuesta incluye: `totalIngresos` (suma de `payments.amount` con `status=PAID` en el período), `ingresosPorMetodo` (desglose por REDSYS y CASH), `numeroPagos` (conteo de pagos PAID), Y la moneda es siempre EUR (NUMERIC(12,2))

#### Scenario 4: Período sin pagos — respuesta con totales en cero
- **GIVEN** un ADMIN autenticado Y no existen pagos en estado `PAID` en el rango de fechas consultado
- **WHEN** el ADMIN invoca `GET /api/admin/dashboard/ingresos?fechaInicio=2025-01-01&fechaFin=2025-01-31` (Fase 2)
- **THEN** la respuesta devuelve `totalIngresos=0.00`, `numeroPagos=0`, `ingresosPorMetodo={"REDSYS":0.00,"CASH":0.00}`, Y el código HTTP es 200 (no 404)

### Requirement 3: Exportación de informe de uso en CSV
**El sistema DEBE permitir al ADMIN exportar un informe de uso en formato CSV con los datos de reservas del período seleccionado, sin incluir datos de identificación personal directa (nombres completos) en las columnas de resumen.**

#### Scenario 5: ADMIN exporta informe CSV del mes — descarga generada
- **GIVEN** un ADMIN autenticado Y existen reservas en el período seleccionado
- **WHEN** el ADMIN invoca `GET /api/admin/dashboard/exportar?fechaInicio=2025-05-01&fechaFin=2025-05-31&formato=CSV` (Fase 2)
- **THEN** la respuesta tiene `Content-Type: text/csv`, `Content-Disposition: attachment; filename="informe-uso-2025-05.csv"`, Y el CSV incluye columnas: `fecha`, `horaInicio`, `duracionMinutos`, `estadoReserva`, `metodoPago`, `importePagado`, `numeroParticipantes`, `canal` (WEB/TELEGRAM), Y el CSV NO incluye `first_name`, `last_name`, `email`, `phone` de los titulares (RN-RGPD-04 y RN-ADM-04)

## Casos límite
- El rango de fechas máximo consultable en un solo request es de 12 meses para evitar timeouts de consulta sin paginación; rangos mayores deben dividirse en múltiples peticiones.
- En v1.0, si el frontend realiza múltiples consultas paginadas a `GET /api/admin/reservas` para construir las métricas, el total de páginas puede ser elevado para períodos largos; el ADMIN debe ser informado de la limitación.
- Los slots de "horario de apertura" para el cálculo de ocupación deben basarse en un horario configurado (pendiente de resolución de decisión abierta P4 en `docs/data-model.md`); hasta que se resuelva, el denominador del porcentaje se calcula considerando 24h disponibles.
- Las métricas de Fase 2 deben estar protegidas por el mismo rate limiting aplicado al resto de endpoints admin.
- La exportación CSV de períodos de más de 6 meses se genera de forma asíncrona (respuesta 202 + URL de descarga) para evitar bloquear el hilo HTTP; en períodos menores, respuesta síncrona 200.

## Dependencias con otras capabilities
- **`reservas`**: fuente principal de datos de ocupación. En v1.0, el frontend usa `GET /api/admin/reservas` con filtros de fecha y estado.
- **`pagos-redsys`**: fuente de datos de ingresos. En v1.0, el frontend usa `GET /api/admin/pagos?status=PAID` con filtros de fecha.
- **`auditoria`**: las consultas de dashboard por ADMIN generan entradas `ADMIN_USER_DATA_ACCESS` en `audit_log` para trazabilidad de acceso a datos agregados.
- **`exportaciones-rgpd`**: los informes CSV deben respetar las restricciones RGPD sobre exposición de datos personales (RN-RGPD-04); la capability de exportaciones-rgpd define qué campos están sujetos a protección.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 21 | Dashboard club | Desktop | ADMIN | [`06-dashboard-club.html`](../../../docs/ux/mockups/06-dashboard-club.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo admin — gestión del club** — el dashboard (pantalla 21) es la pantalla principal del flujo admin; muestra KPIs de reservas, ingresos, ocupación y cancelaciones que en v1.0 el frontend agrega a partir de los endpoints existentes de `reservas` y `pagos-redsys`.
