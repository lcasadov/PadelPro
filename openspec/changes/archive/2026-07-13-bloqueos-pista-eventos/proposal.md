## Why

El club necesita reservar la pista para torneos o eventos, dejando esas horas **no reservables** por los jugadores. Hoy no existe forma de hacerlo por franja: `DisponibilidadService` genera los tramos 8:00–23:00 y solo resta reservas activas; el único mecanismo es poner toda la pista en MANTENIMIENTO (RN-RES-04), que oculta el día entero. Falta un bloqueo **granular** de horas concretas de una fecha.

## What Changes

- Nuevo concepto **bloqueo de franja**: el ADMIN puede marcar horas concretas de una fecha como bloqueadas (con un motivo), y desbloquearlas.
- Endpoints ADMIN: crear bloqueo(s), listar por fecha y eliminar un bloqueo.
- Al crear un bloqueo, si **ya hay reservas activas** que solapan esa franja, la operación se **impide** y se informa de las reservas en conflicto (el admin las gestiona antes).
- La disponibilidad del jugador **excluye** las franjas bloqueadas: una hora bloqueada no aparece como reservable ni como "unible".
- Pantalla de administración: elegir fecha → rejilla horaria del día (libre / reservada / bloqueada) → marcar franjas libres como bloqueadas con un motivo → guardar; aviso ante conflicto.

## Capabilities

### New Capabilities

- `bloqueos-pista`: gestión por el ADMIN de bloqueos de franjas horarias de la pista (crear con chequeo de conflicto contra reservas activas, listar por fecha, eliminar) para torneos/eventos.

### Modified Capabilities

- `disponibilidad-pistas`: el cálculo de tramos disponibles DEBE excluir las franjas bloqueadas para la fecha consultada, además de las ya ocupadas por reservas activas.

## Impact

- **Backend**: entidad `BloqueoPista` (fecha + hora de franja + motivo + `created_by`) con migración Flyway (V19); repositorio; servicio de bloqueos con validación de solape contra reservas activas (reusa `ReservationQueryPort`/ocupación); endpoints `POST`/`GET`/`DELETE /api/admin/bloqueos`; integración en `DisponibilidadService` + invalidación de la caché `available-slots` al crear/borrar.
- **Frontend**: nueva página admin de bloqueos (rejilla horaria por fecha), servicio de API, enlace en Home admin.
- **APIs**: nuevos endpoints admin de bloqueos; sin cambios en el contrato de disponibilidad del jugador (solo cambia el conjunto de tramos devueltos).
- **Datos**: nueva tabla `bloqueo_pista`. No afecta a reservas existentes.

## Fuera de alcance

- **Bloqueos recurrentes** (p.ej. "todos los lunes 20:00"): solo bloqueos de fecha+hora puntuales.
- Reubicar/cancelar automáticamente reservas en conflicto: el bloqueo se impide y el admin resuelve el solape manualmente (por decisión de producto).
- Granularidad sub-horaria (30 min): se bloquea por franja de 60 min, alineado con la rejilla actual de disponibilidad.
- Multi-pista: el modelo actual es de pista única (singleton), igual que el resto de capabilities.

## Fase del producto

fase-3 (operación/administración del club).
