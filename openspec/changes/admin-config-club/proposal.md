## Why

El backend ya expone la configuración global del club (`GET`/`PATCH /api/admin/sistema/config`: precio/hora, estado de pista, aforo, plazo de cancelación, pasarela de pago, y credenciales Telegram cifradas), pero **el panel de administración no tiene ninguna pantalla para editarla**: hoy solo se puede cambiar llamando a la API con curl/Postman y un JWT de ADMIN. Esto bloquea tareas cotidianas del club (fijar el precio de la pista) y, en particular, **impide configurar el bot de Telegram desde la UI**, que es requisito para activar las notificaciones y la vinculación OTP.

## What Changes

- Nueva pantalla de administración **`/admin/config`** (solo ADMIN) que lee y edita la configuración global vía los endpoints existentes.
- Campos editables: nombre y descripción del club, **precio por hora**, **estado de la pista** (ACTIVA/MANTENIMIENTO), aforo máximo por pista, plazo de cancelación (horas), pasarela de pago, y **credenciales de Telegram** (bot token, webhook secret, group id).
- Los secretos (bot token, webhook secret, claves Redsys) se muestran **enmascarados**; dejarlos en blanco conserva el valor almacenado (semántica PATCH parcial, ya soportada por el backend).
- Enlace "Configuración" en la Home de administración.
- Sin cambios de esquema ni de contrato de API: se consume el endpoint tal cual.

## Capabilities

### New Capabilities

_Ninguna._

### Modified Capabilities

- `configuracion-club`: se añade un requisito de **interfaz de administración** — el panel ADMIN DEBE ofrecer una pantalla para leer y actualizar la configuración global, respetando el enmascarado de secretos y la semántica PATCH parcial. No cambia ningún requisito de backend existente (Requisitos 1–4 se mantienen).

## Impact

- **Frontend**: nueva página `ConfiguracionPage` + servicio de API (`GET`/`PATCH /api/admin/sistema/config`), ruta protegida por rol ADMIN, enlace en `HomePage`. Tests de componente (Vitest + MSW) y E2E de edición de precio.
- **Backend**: sin cambios de código; solo verificación de que el `PATCH` parcial y el enmascarado de secretos se comportan según el spec `configuracion-club`.
- **APIs**: ninguna nueva; se consumen `GET`/`PATCH /api/admin/sistema/config`.
- **Desbloquea**: la configuración de Telegram desde la UI (prerequisito para el round-trip real de vinculación/notificaciones).

## Fuera de alcance

- Mostrar el precio por hora al jugador en la UI de reserva/confirmación (posible mejora posterior).
- Bloqueo de franjas horarias para eventos → change separado `bloqueos-pista-eventos`.
- Cualquier cambio en el modelo de datos de `system_config` (columnas nuevas, horario de apertura configurable, etc.).

## Fase del producto

fase-3 (operación/administración del club sobre las capabilities ya entregadas).
