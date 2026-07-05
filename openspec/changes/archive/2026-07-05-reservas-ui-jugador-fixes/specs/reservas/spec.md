## ADDED Requirements

### Requirement: Manejo de sesión caducada al crear reserva (UI jugador)
Cuando `POST /api/reservas` responde **401 `AUTH_REQUIRED`** (access token caducado o ausente), la UI SHALL mostrar un mensaje claro de sesión caducada y SHALL ofrecer/realizar la **redirección a la pantalla de login**, en lugar del mensaje genérico "No se pudo completar la reserva". No SHALL sugerir "reintentar" la misma petición sin re-autenticar.

#### Scenario: Sesión caducada al confirmar
- **WHEN** el usuario confirma la reserva y el backend responde 401 `AUTH_REQUIRED`
- **THEN** la UI muestra "tu sesión ha caducado, vuelve a iniciar sesión" y lleva al usuario a la pantalla de login

#### Scenario: Reserva válida con sesión vigente se crea con éxito
- **WHEN** el usuario confirma una franja libre y futura con datos válidos y sesión no caducada
- **THEN** el backend responde 201 y la UI muestra la reserva creada (estado pendiente de confirmación), sin ningún mensaje de error

### Requirement: Manejo de errores accionable al crear reserva (UI jugador)
La UI de confirmación de reserva SHALL traducir cada error del backend a un mensaje específico y accionable, leyendo el contrato de error real (`error` como código + `details` como lista), y SHALL reservar el mensaje genérico "No se pudo completar la reserva" únicamente para fallos verdaderamente desconocidos (5xx sin código o error de red).

#### Scenario: Error de validación muestra el detalle del backend
- **WHEN** el backend responde 400 `VALIDATION_ERROR` con `details` no vacío
- **THEN** la UI muestra el primer detalle del backend (p. ej. el campo inválido), no el mensaje genérico

#### Scenario: Franja no futura
- **WHEN** el usuario intenta reservar una franja cuya fecha+hora ya pasó
- **THEN** la UI muestra un mensaje claro indicando que la franja ya no es reservable y ofrece volver a disponibilidad

#### Scenario: Fallo interno del servidor no se enmascara como dato inválido
- **WHEN** el backend responde 5xx
- **THEN** la UI muestra un mensaje de error del servidor con opción de reintentar, distinto de un error de datos del usuario

### Requirement: Selección de duración de la reserva (UI jugador)
La UI de reserva SHALL permitir al usuario elegir la duración entre **60, 90 y 120 minutos** en franjas de media hora, y SHALL enviar la duración elegida en `durationMinutes`. El `startTime` SHALL mantenerse en minutos 00 o 30. (El backend acepta también 150 y 180; la UI no los ofrece en esta fase.)

#### Scenario: Elegir duración de 90 minutos
- **WHEN** el usuario selecciona una franja y elige duración 90 min
- **THEN** la UI envía `durationMinutes: 90` y el resumen refleja "90 min"

#### Scenario: Solo se ofrecen duraciones válidas para la franja
- **WHEN** una duración haría que la reserva solape con otra o supere el horario disponible
- **THEN** esa duración no se ofrece como opción seleccionable

#### Scenario: Duración por defecto
- **WHEN** el usuario abre la confirmación sin elegir duración
- **THEN** la UI preselecciona 60 min como valor por defecto

### Requirement: Distinción entre compañero registrado e invitado externo (UI jugador)
Al añadir un participante adicional, la UI SHALL permitir elegir entre (a) **socio registrado** —seleccionado de entre los usuarios dados de alta, enviando `userId`— o (b) **invitado externo** —introduciendo nombre y teléfono, enviando `externalName`/`externalPhone`—. La UI SHALL enviar exactamente uno de los dos (XOR), acorde a la validación del backend.

#### Scenario: Añadir un socio registrado
- **WHEN** el usuario elige el tipo "socio registrado" y selecciona un usuario de la lista
- **THEN** el participante se envía con `userId` y sin `externalName`

#### Scenario: Añadir un invitado externo
- **WHEN** el usuario elige el tipo "invitado externo" e introduce nombre y teléfono
- **THEN** el participante se envía con `externalName` (y `externalPhone` si se indicó) y sin `userId`

#### Scenario: No se permite un participante ambiguo o vacío
- **WHEN** un participante no tiene ni socio seleccionado ni nombre externo
- **THEN** la UI impide continuar y señala que debe completarse ese participante

### Requirement: Navegación a Inicio desde las páginas de reservas (UI jugador)
Cada página del flujo de reservas (disponibilidad, confirmar, mis reservas, detalle) SHALL ofrecer un control visible para volver a la pantalla de Inicio (Home).

#### Scenario: Volver a Inicio desde cualquier página de reservas
- **WHEN** el usuario está en cualquiera de las páginas de reservas y activa el control de Inicio
- **THEN** la aplicación navega a la pantalla Home

### Requirement: Acciones sobre la reserva desde "Mis Reservas" (UI jugador)
La pantalla "Mis Reservas" SHALL permitir, por cada reserva y sin necesidad de entrar al detalle: (a) **cancelar** la reserva cuando proceda según su estado y propiedad; (b) **visualizar el estado del pago**; (c) **elegir el método de pago** entre *pago en diferido* (presencial/efectivo confirmado por el club) o *pagar ahora* (pago online, dependiente de la capability `pagos-redsys`).

#### Scenario: Cancelar desde la lista
- **WHEN** el usuario es owner de una reserva cancelable y activa "Cancelar" en su tarjeta
- **THEN** la reserva se cancela (DELETE `/api/reservas/{id}`) y la lista refleja el nuevo estado

#### Scenario: Ver estado de pago
- **WHEN** la reserva tiene un pago asociado
- **THEN** la tarjeta muestra el estado del pago (p. ej. pendiente / pagado)

#### Scenario: Elegir pago en diferido
- **WHEN** el usuario elige "pago en diferido" para una reserva pendiente de pago
- **THEN** la UI indica que el cobro se realizará de forma presencial y no inicia ningún pago online

#### Scenario: Elegir pagar ahora sin pasarela disponible
- **WHEN** el usuario elige "pagar ahora" y la capability `pagos-redsys` aún no está disponible
- **THEN** la UI comunica que el pago online no está disponible todavía y mantiene la opción de pago en diferido

#### Scenario: Acción no disponible por estado
- **WHEN** una reserva está en un estado no cancelable o ya pagada
- **THEN** la UI no ofrece la acción correspondiente y lo refleja visualmente
