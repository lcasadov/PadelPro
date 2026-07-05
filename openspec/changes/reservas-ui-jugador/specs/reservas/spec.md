## ADDED Requirements

### Requirement: Buscar disponibilidad de pistas (UI jugador)

La interfaz web SHALL permitir al usuario autenticado seleccionar una fecha y ver los tramos disponibles devueltos por `GET /api/reservas/disponibles?fecha=YYYY-MM-DD`. La UI SHALL ofrecer la acción "Reservar" únicamente en los tramos marcados como `creable` por el backend; los tramos no reservables (parciales, solo unibles) NO SHALL presentarse como acción de crear (RN-RES-01/RN-RES-02). La UI SHALL mostrar los tramos tal como los devuelve el backend, sin construir combinaciones hora×duración en el cliente.

#### Scenario: Ver tramos reservables para una fecha
- **WHEN** el usuario autenticado selecciona una fecha con tramos `creable`
- **THEN** la UI muestra esos tramos con su `horaInicio` y `duracionMinutos` y la acción de reservar habilitada

#### Scenario: Tramos parciales no ofrecen crear
- **WHEN** un tramo tiene `creable: false` (ya existe una reserva incompleta en esa franja)
- **THEN** la UI no ofrece la acción de crear reserva para ese tramo

#### Scenario: Sin disponibilidad o pista en mantenimiento
- **WHEN** el backend devuelve `tramosDisponibles` vacío (incluida pista en MANTENIMIENTO)
- **THEN** la UI muestra un estado vacío sin revelar el motivo del mantenimiento

#### Scenario: Seleccionar un tramo reservable lleva a confirmar
- **WHEN** el usuario selecciona un tramo con `creable: true`
- **THEN** la UI navega a la pantalla de confirmación con la fecha, hora de inicio y duración de ese tramo

### Requirement: Confirmar y crear reserva (UI jugador)

La interfaz web SHALL permitir confirmar una reserva enviando `POST /api/reservas` con header `Idempotency-Key` y payload `{ reservationDate, startTime, durationMinutes, participantesAdicionales?, notes? }`. La UI SHALL mostrar el precio total **devuelto por el backend** y NUNCA calcularlo ni enviarlo desde el cliente (RN-RES-03). La `Idempotency-Key` SHALL generarse en el cliente por intento y reutilizarse en reintentos de red del mismo envío, regenerándose si el usuario cambia los datos del formulario (RN-RES-05).

#### Scenario: Confirmación exitosa (pago CASH)
- **WHEN** el usuario confirma una reserva con datos válidos
- **THEN** la UI envía `POST /api/reservas` con `Idempotency-Key` y, al recibir 201 con estado `PENDING_CONFIRMATION`, muestra el mensaje "reserva pendiente de confirmación por el club" sin pantalla de pago online

#### Scenario: Participantes adicionales opcionales
- **WHEN** el usuario añade participantes adicionales indicando su `externalName`
- **THEN** la UI incluye `participantesAdicionales` en el payload respetando el límite del backend

#### Scenario: Límite de participantes excedido
- **WHEN** el backend responde 422 `PARTICIPANTS_LIMIT_EXCEEDED`
- **THEN** la UI muestra un mensaje indicando que se ha superado el número máximo de participantes (RN-RES-02)

#### Scenario: Franja ocupada durante la confirmación
- **WHEN** el backend responde 409 `CONFLICT` al crear la reserva
- **THEN** la UI muestra "la franja se acaba de ocupar" y ofrece un botón para volver a la búsqueda / refrescar la disponibilidad

#### Scenario: Datos de reserva inválidos
- **WHEN** el backend responde 400 `VALIDATION_ERROR` (fecha pasada, duración no permitida o `startTime` fuera de :00/:30)
- **THEN** la UI lee el `code` y los `errors[]` del cuerpo `{ code, message, errors[] }` y muestra un mensaje específico

#### Scenario: Transición de estado inválida
- **WHEN** el backend responde 422 `INVALID_STATE_TRANSITION`
- **THEN** la UI muestra un mensaje indicando que la operación no es válida para el estado actual de la reserva

#### Scenario: Reintento de red no duplica la reserva
- **WHEN** un envío falla por red y el usuario reintenta sin cambiar los datos
- **THEN** la UI reutiliza la misma `Idempotency-Key` y el backend devuelve la reserva existente sin crear un duplicado

### Requirement: Listar mis reservas (UI jugador)

La interfaz web SHALL mostrar las reservas del usuario autenticado obtenidas de `GET /api/reservas`, incluyendo el estado de la reserva (`PENDING_CONFIRMATION`, `CONFIRMED`, `CANCELLED`, `COMPLETED`) y el estado del pago asociado.

#### Scenario: Ver lista de reservas propias
- **WHEN** el usuario autenticado abre "Mis reservas"
- **THEN** la UI muestra sus reservas (como owner o participante) con estado de reserva y estado de pago

#### Scenario: Sin reservas
- **WHEN** el usuario no tiene reservas
- **THEN** la UI muestra un estado vacío con acceso a buscar disponibilidad

### Requirement: Detalle y cancelación de reserva (UI jugador)

La interfaz web SHALL mostrar el detalle de una reserva desde `GET /api/reservas/{id}` y SHALL ofrecer el botón de cancelar (`DELETE /api/reservas/{id}`) únicamente cuando el usuario autenticado es el owner (comparando `ReservaResponse.ownerId` con el `id` del usuario obtenido de `getMeApi` y cacheado en `AuthContext`) y el estado es cancelable. La UI SHALL respetar la respuesta del backend tanto para reservas ajenas (403) como inexistentes (404) sin exponer datos (RN-RGPD-03) y SHALL comunicar el resultado de la cancelación según el `code` devuelto.

#### Scenario: Cancelación dentro de plazo
- **WHEN** el owner cancela una reserva dentro del plazo permitido
- **THEN** la UI envía `DELETE /api/reservas/{id}`, recibe 204 y refleja la reserva como cancelada

#### Scenario: Cancelación fuera de plazo
- **WHEN** el backend responde 422 `CANCELLATION_DEADLINE_PASSED`
- **THEN** la UI muestra un mensaje claro indicando que la cancelación está fuera de plazo y que no aplica reembolso (RN-RES-04)

#### Scenario: Botón cancelar visible solo para el owner
- **WHEN** el `id` del usuario autenticado coincide con `ownerId` y el estado es cancelable
- **THEN** la UI muestra el botón de cancelar

#### Scenario: Botón cancelar oculto si no procede
- **WHEN** el usuario no es el owner (su `id` no coincide con `ownerId`) o el estado no es cancelable
- **THEN** la UI no muestra el botón de cancelar

#### Scenario: Acceso a reserva ajena
- **WHEN** el backend responde 403 al pedir una reserva que no pertenece al usuario
- **THEN** la UI muestra un mensaje de acceso denegado sin revelar datos de la reserva

#### Scenario: Reserva inexistente
- **WHEN** el backend responde 404 al pedir una reserva que no existe
- **THEN** la UI muestra un mensaje de reserva no encontrada

### Requirement: Acceso a reservas desde el Home (UI jugador)

La interfaz web SHALL enlazar el botón "Reservar pista" de la página de inicio con la pantalla de búsqueda de disponibilidad, y SHALL proteger todas las rutas de reservas del jugador tras autenticación (`PrivateRoute`).

#### Scenario: Botón "Reservar pista" navega a disponibilidad
- **WHEN** el usuario autenticado pulsa "Reservar pista" en el Home
- **THEN** la UI navega a la pantalla de búsqueda de disponibilidad

#### Scenario: Rutas de reservas protegidas
- **WHEN** un usuario no autenticado intenta acceder a una ruta de reservas
- **THEN** la UI redirige al login
