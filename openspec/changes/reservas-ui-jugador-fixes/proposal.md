## Why

El change `reservas-ui-jugador` entregó las pantallas del jugador, pero en uso real (feedback 2026-07-05) el flujo central no se puede completar: **crear una reserva devuelve un error genérico** y varias funcionalidades acordadas no están: no se puede elegir la duración (solo 60 min), no se distingue un compañero registrado de uno externo, no hay forma de volver a Inicio, y **"Mis Reservas" no cumple su función** (solo enlaza al detalle; no permite cancelar, ver el estado del pago ni pagar). Sin estos ajustes, la funcionalidad núcleo del producto sigue siendo inutilizable para el jugador aunque la API ya soporte la mayor parte.

**Causa raíz del error al crear, confirmada en vivo (E2E 2026-07-05):** el backend crea la reserva correctamente (HTTP 201); el fallo es de **sesión caducada en el frontend**. El access token JWT vive solo en memoria y caduca a los 15 min (900s), sin refresh silencioso ni interceptor de 401. Pasado ese tiempo, `POST /api/reservas` responde **401 `AUTH_REQUIRED`**, que el frontend no contempla (`codeFromStatus` ignora 401 → `UNKNOWN`) y traduce al mensaje genérico "No se pudo completar la reserva. Inténtalo de nuevo." No hay mensaje de "sesión caducada" ni redirección a login, así que reintentar vuelve a fallar. Es un defecto **global de sesión** (afecta a toda escritura de la app), no específico de reservas.

## What Changes

Corregir y completar la capa de interfaz del jugador. El backend ya soporta la mayoría de lo que falta; el grueso es frontend, con un delta de backend acotado para el bug de creación y (opcional) la búsqueda de socios.

- **[BUG] Manejo de sesión caducada al crear reserva.** El frontend debe detectar el **401 `AUTH_REQUIRED`** y mostrar un mensaje claro de "sesión caducada" con **redirección a login**, en vez del genérico "No se pudo completar la reserva". Se corrige además el mapeo de errores para leer el contrato real (`error` como código + `details` como lista, no `err.fieldErrors`), de modo que 400/409/422 muestren mensaje específico. Una reserva válida con sesión vigente ya se crea con éxito (verificado: HTTP 201).
  - **[Global, fuera de este change] Refresh silencioso de sesión.** El arreglo de fondo —interceptor que ante 401 renueva el access token con la cookie `refresh_token` (7 días) y reintenta— es un defecto de `auth-local` que afecta a toda la app; se aborda en un **change propio** (`auth-session-refresh`). Aquí solo se maneja el 401 localmente para que el jugador no quede atascado.

- **Selector de duración** en el flujo de reserva: **60 / 90 / 120 min** en franjas de media hora, respetando `startTime` en minutos 00/30 (el backend acepta hasta 180, pero la UI ofrece estas tres). Hoy el frontend propaga una duración fija del tramo sin dar a elegir.

- **Compañero registrado vs. invitado externo.** Al añadir un participante, distinguir dos tipos: (a) **socio registrado** — se elige mediante un **buscador de socios** (por nombre/email) y se envía `userId`; (b) **invitado externo** — se introduce nombre y teléfono y se envía `externalName`/`externalPhone`. El backend ya aplica el XOR `userId` vs `externalName`; falta la UI y un **endpoint nuevo de búsqueda de usuarios** que alimente el buscador.

- **Navegación a Inicio** desde todas las páginas de reservas (Disponibilidad, Confirmar, Mis Reservas, Detalle): enlace/botón visible a Home. Hoy ninguna enlaza y el logo es texto estático.

- **"Mis Reservas" funcional.** Desde la lista, para cada reserva: (a) **cancelar** (sin entrar al detalle); (b) **visualizar el estado del pago**; (c) **elegir método de pago**: *pago en diferido* (efectivo/presencial confirmado por el club — comportamiento actual del MVP) o *pagar ahora mediante Redsys*.

- **"Pagar ahora" (Redsys) queda como punto de entrada deshabilitado.** La UI muestra la opción "pagar ahora" informativa/deshabilitada ("disponible próximamente"); el pago online se implementa en un **change propio `pagos-redsys`** (fuera de alcance aquí). Así cancelar + ver estado + pago en diferido se entregan **sin bloquear** con Redsys.

## Capabilities

### New Capabilities
<!-- Ninguna capability nueva en este change. El pago online Redsys se aborda en un change propio `pagos-redsys` (ya existe el spec a nivel de diseño en openspec/specs/). -->

### Modified Capabilities
- `reservas`: se corrigen y amplían los requirements de la interfaz web del jugador — manejo de sesión caducada (401) al crear con mensaje claro + redirección a login, alineación del mapeo de error al contrato real, selección de duración, distinción de participante registrado vs. externo, navegación a Inicio, y acciones de cancelar / ver estado de pago / elegir método de pago desde "Mis Reservas" (con "pagar ahora" deshabilitado hasta el change `pagos-redsys`). La lógica de dominio del backend no cambia (crea reservas correctamente, ya soporta duraciones, XOR de participantes y cancelación); el arreglo del bug es de frontend.
- `usuarios`: se añade un endpoint de **búsqueda de usuarios registrados** (por nombre/email, resultados mínimos: id + nombre) para alimentar el buscador de compañero registrado.

## Impact

- **Fase del producto**: fase-1 (`reservas` es fase-1; el pago diferido y las correcciones son fase-1). El pago online Redsys se aborda en el change propio `pagos-redsys` (fase-2), fuera de este change.
- **Frontend** (`frontend/src/`): `reservasApi.ts` / `messageForError` (mapear 401 `AUTH_REQUIRED` a "sesión caducada" + alinear al contrato real `error`/`details`), `ConfirmarReservaPage` (manejo del 401 con redirección a login + selector de duración 60/90/120 + tipos de participante con buscador de socios), `MisReservasPage` (acciones cancelar / estado pago / método de pago con "pagar ahora" deshabilitado), navegación a Home en las 4 páginas, `reservasApi.ts` (búsqueda de usuarios). Nuevos componentes de selector de participante y de método de pago.
- **Backend** (`backend/src/main/java/com/padelpro/`):
  - `reservas`: sin cambios de dominio (crea reservas correctamente). Verificar únicamente el contrato del cuerpo de error (`error` + `details`) que consume el frontend.
  - `usuarios`: endpoint `GET` de búsqueda de usuarios (nombre/email, paginado/limit, resultados mínimos id+nombre).
- **Contrato / `docs/openapi.yaml`**: documentar el endpoint de búsqueda de usuarios; alinear el schema del cuerpo de error de reservas con lo que emite el backend.
- **Datos / migraciones**: ninguna.
- **Sin impacto** en autenticación, roles ni en el cálculo de disponibilidad.

## Fuera de alcance

- **Refresh silencioso de sesión / interceptor global de 401** (renovar el access token con la cookie `refresh_token` y reintentar la petición) — es un defecto global de `auth-local` que se aborda en un **change propio `auth-session-refresh`**. Este change solo maneja el 401 localmente en el flujo de reserva (mensaje "sesión caducada" + redirección a login).
- **Pago online Redsys completo** (iniciar pago, firma HMAC, TPV, webhook, conciliación) — se traslada a un **change propio `pagos-redsys`**; este change solo deja el punto de entrada "pagar ahora" en la UI, deshabilitado e informativo.
- Vistas de administración de reservas (calendario semanal, tabla del club, confirmar/cambiar estado desde UI) — change posterior.
- Unirse a partidas / ocupar plazas libres de reservas de terceros — capability `partidas`.
- Notificaciones de reserva (email/Telegram) — capability `notificaciones`.
- Reembolsos automáticos más allá del marcado de estado existente al cancelar.
- Rediseño visual de las pantallas; se mantienen los design tokens y mockups actuales.
