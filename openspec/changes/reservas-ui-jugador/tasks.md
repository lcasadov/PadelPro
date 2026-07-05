> **Proceso TDD estricto**: para cada unidad, escribir el test primero (🔴 Red), implementar el mínimo para pasarlo (🟢 Green) y refactorizar (♻️ Refactor). No implementar producción sin un test que falle antes. Stack frontend: vitest + testing-library + MSW. Implementación: `backend-architect` (grupo 1) y `frontend-engineer` (resto). **El grupo 1 (backend) es dependencia de los grupos 3 y 7 del frontend** y debe desplegarse antes.

## 1. Delta backend — flag `creable` en disponibilidad (backend-architect)

- [x] 1.1 🔴 Test unitario de `DisponibilidadService`: un tramo sin ocupación (`plazasLibres == max_participants`) devuelve `creable: true`
- [x] 1.2 🔴 Test: un tramo con reserva incompleta (`plazasLibres < max_participants`) devuelve `creable: false`
- [x] 1.3 🟢 Añadir campo `creable` al record `TramoDisponible` y setearlo en `DisponibilidadService` (`creable = plazasLibres == maxParticipants`, equivalente a `occupied == 0`)
- [x] 1.4 🟢 Actualizar `docs/openapi.yaml`: añadir `creable` (boolean, required) al schema `TramoDisponible`
- [x] 1.5 ♻️ Verificar que el cálculo existente de `plazasLibres` y el set de tramos NO cambian (retrocompatibilidad)

## 2. Servicio `reservasApi.ts` (frontend-engineer)

- [x] 2.1 🔴 Test: `getDisponibilidad(token, fecha)` llama a `GET /api/reservas/disponibles?fecha=` y devuelve `tramosDisponibles` (incluye `creable`), con caso lista vacía
- [x] 2.2 🔴 Test: `crearReserva(token, payload, idempotencyKey)` envía `POST /api/reservas` con header `Idempotency-Key` y NO incluye ningún importe en el payload (RN-RES-03)
- [x] 2.3 🔴 Test: mapeo de errores por el campo `code` del shape `{ code, message, errors[] }` — 409 CONFLICT, 400 VALIDATION_ERROR (con `errors[]`), 422 PARTICIPANTS_LIMIT_EXCEEDED / INVALID_STATE_TRANSITION / CANCELLATION_DEADLINE_PASSED, 403, 404
- [x] 2.4 🔴 Test: `getMisReservas(token)`, `getReserva(token, id)`, `cancelarReserva(token, id)` (204) golpean los endpoints correctos
- [x] 2.5 🟢 Implementar `reservasApi.ts` (axios `create({ baseURL: '/api', withCredentials: true })`, `token` por llamada) con los 5 métodos y el mapeo de errores por `code` (D6)
- [x] 2.6 ♻️ Refactor: extraer tipos (`Tramo` con `creable`, `ReservaResponse`, `CrearReservaPayload`, errores tipados por `code`) y helper de `Idempotency-Key`

## 3. Identidad de usuario en `AuthContext` (frontend-engineer)

- [x] 3.1 🔴 Test: al entrar en el área de reservas se llama a `getMeApi(token)` una vez y el `userId` queda disponible en el contexto (D8)
- [x] 3.2 🟢 Añadir `userId` a la sesión de `AuthContext` y carga lazy vía `getMeApi`; limpiar en logout
- [x] 3.3 ♻️ Refactor: evitar recargas repetidas (cachear el `userId` mientras la sesión viva)

## 4. Página "Buscar disponibilidad" (mockup 03)

- [x] 4.1 🔴 Test: renderiza selector de fecha y lista los tramos desde el service (MSW)
- [x] 4.2 🔴 Test: solo los tramos `creable: true` ofrecen la acción de reservar; los `creable: false` no
- [x] 4.3 🔴 Test: estado vacío cuando `tramosDisponibles` está vacío (sin revelar mantenimiento)
- [x] 4.4 🔴 Test: seleccionar un tramo `creable` navega a "Confirmar" pasando fecha + hora + duración
- [x] 4.5 🟢 Implementar `DisponibilidadPage` (+ CSS module) consumiendo `getDisponibilidad`, con design tokens existentes
- [x] 4.6 ♻️ Refactor: extraer componente de tarjeta/fila de tramo y estados de carga/error

## 5. Página "Confirmar reserva" (mockup 04)

- [x] 5.1 🔴 Test: muestra fecha/hora/duración elegidas y el **precio total devuelto por el backend** (nunca calculado en cliente)
- [x] 5.2 🔴 Test: añadir participantes adicionales (`externalName`) los incluye en el payload
- [x] 5.3 🔴 Test: confirmar exitoso (201, `PENDING_CONFIRMATION`) muestra "reserva pendiente de confirmación por el club" sin pantalla de pago
- [x] 5.4 🔴 Test: 409 CONFLICT muestra "la franja se acaba de ocupar" + botón volver/refrescar disponibilidad (D5)
- [x] 5.5 🔴 Test: 400 VALIDATION_ERROR y 422 PARTICIPANTS_LIMIT_EXCEEDED / INVALID_STATE_TRANSITION muestran mensajes específicos por `code`
- [x] 5.6 🔴 Test: `Idempotency-Key` se reutiliza en reintento sin cambios y se regenera al cambiar datos del formulario (D2)
- [x] 5.7 🟢 Implementar `ConfirmarReservaPage` (+ CSS module) con generación/gestión de `Idempotency-Key` (`crypto.randomUUID`)
- [x] 5.8 ♻️ Refactor: extraer gestión de estado del formulario y del key de idempotencia

## 6. Página "Mis reservas" (mockup 05)

- [x] 6.1 🔴 Test: lista reservas del usuario con estado de reserva y estado de pago desde `getMisReservas` (MSW)
- [x] 6.2 🔴 Test: estado vacío con acceso a buscar disponibilidad
- [x] 6.3 🟢 Implementar `MisReservasPage` (+ CSS module)
- [x] 6.4 ♻️ Refactor: extraer badge de estado (reserva/pago) reutilizable

## 7. Página "Detalle de reserva" (mockup 13)

- [x] 7.1 🔴 Test: muestra datos completos de la reserva desde `getReserva(token, id)`
- [x] 7.2 🔴 Test: botón cancelar visible solo si `me.id == ownerId` y estado cancelable; oculto en caso contrario (D8)
- [x] 7.3 🔴 Test: cancelación dentro de plazo (204) refleja la reserva como cancelada
- [x] 7.4 🔴 Test: 422 CANCELLATION_DEADLINE_PASSED muestra mensaje claro de fuera de plazo + no aplica reembolso (RN-RES-04)
- [x] 7.5 🔴 Test: 403 (ajena) muestra acceso denegado y 404 (inexistente) muestra reserva no encontrada, sin exponer datos (RN-RGPD-03)
- [x] 7.6 🟢 Implementar `DetalleReservaPage` (+ CSS module) con lógica de visibilidad del botón cancelar
- [x] 7.7 ♻️ Refactor: extraer confirmación de cancelación y mapeo de mensajes de error

## 8. Navegación y rutas (frontend-engineer)

- [x] 8.1 🔴 Test: el botón "Reservar pista" del Home navega a la búsqueda de disponibilidad
- [x] 8.2 🔴 Test: las rutas de reservas están protegidas por `PrivateRoute` (usuario no autenticado → login)
- [x] 8.3 🟢 Conectar botón del `HomePage` y registrar rutas privadas en `App.tsx` (disponibilidad, confirmar, mis reservas, detalle)
- [x] 8.4 ♻️ Refactor: revisar enlaces de navegación entre las 4 páginas (flujo buscar → confirmar → mis reservas → detalle)

## 9. QA end-to-end

- [x] 9.1 Ejecutar suite completa (backend + vitest) y verificar cobertura de los nuevos módulos
- [x] 9.2 `verification-specialist`: build + lint + tests + probes de manejo de errores por `code` (incluido 404 y `creable`)
- [x] 9.3 `reality-checker`: journey en vivo (stack Docker db+backend+frontend). Veredicto **NEEDS WORK** → 3 bugs por drift de contrato frontend↔backend real (el frontend siguió `docs/openapi.yaml`, desactualizado): (1) `GET /api/reservas` devuelve array plano → MisReservasPage siempre vacía; (2) error body `{error,message}` con HTTP 422 → mensajes 422 genéricos; (3) participantes `owner`/`externalName` (no `isOwner`/`nombre`) → jugadores mal en detalle. Happy path crear+idempotencia ✅, cancelar/botón ✅
- [x] 9.4 Corregir bugs detectados (bug loop) hasta QA en verde — fix commiteado en `5dae956` (alinear frontend + mocks MSW/tests al contrato real). QA automatizada ✅ (tsc + vitest 126/126 + backend disponibilidad). Re-verificación en vivo con `reality-checker` (2026-07-05, stack Docker db+backend+frontend) → **PASS**: journey API completo (login→disponibilidad→crear 201 PENDING→listar array plano→detalle owner/externalName→409 CONFLICT/422 PARTICIPANTS_LIMIT_EXCEEDED→cancelar 204) + 34/34 tests de componente. Los 3 bugs de drift confirmados resueltos contra el contrato real del backend. Salvedad: capa visual por navegador no accionada (sin extensión Chrome conectada); bugs eran de mapeo de datos, falsados vía API. Backend real usa error shape `{error,message,timestamp}` (clave `error`), no `{code,...}` — el frontend ya prioriza `body.error ?? body.code`
