## ADDED Requirements

### Requirement: Listado de partidas abiertas con identificador
El sistema SHALL exponer a un usuario autenticado el listado de partidas abiertas (reservas activas en `PENDING_CONFIRMATION`/`CONFIRMED` con plazas libres) para una fecha, cada una identificable por `reservaId` y con fecha, hora, duración, plazas libres, participantes (nombre de display) e importe total (informativo). No SHALL exponer datos sensibles de los participantes (email, teléfono).

#### Scenario: Ver partidas abiertas de una fecha
- **WHEN** un usuario autenticado consulta las partidas abiertas de una fecha con reservas incompletas
- **THEN** el sistema responde 200 con una lista de partidas, cada una con `reservaId`, hora, duración y plazas libres

#### Scenario: Reserva completa no aparece
- **WHEN** una reserva ya tiene el máximo de participantes
- **THEN** no aparece en el listado de partidas abiertas

#### Scenario: Requiere autenticación
- **WHEN** se consulta el listado sin credenciales válidas
- **THEN** el sistema responde 401

### Requirement: Unirse a una partida con validación atómica de plaza
El sistema SHALL añadir al usuario autenticado como participante no-owner de la reserva vía `POST /api/reservas/{id}/unirse`, asignando el siguiente `slot_position`, siempre que: la reserva exista (si no, 404), esté en estado que admite unión (si no, 422), el usuario no sea ya participante (si lo es, 409) y queden plazas (si no, 422). La comprobación de plaza y la inserción SHALL ser atómicas para que uniones concurrentes no superen `max_participants`.

#### Scenario: Unión exitosa
- **WHEN** un usuario autenticado que no participa envía `POST /api/reservas/{id}/unirse` sobre una reserva activa con plazas libres
- **THEN** el sistema responde 200 con `{participanteId, reservaId, userId, nombre, statusPago}` y la reserva pasa a tener un participante más (`is_owner=false`)

#### Scenario: Ya es participante (RN-AUTH-03)
- **WHEN** el usuario ya figura como participante de la reserva
- **THEN** el sistema responde 409 y no crea un registro duplicado

#### Scenario: Reserva completa (RN-RES-02)
- **WHEN** la reserva ya tiene `max_participants` participantes
- **THEN** el sistema responde 422 indicando que está completa

#### Scenario: Estado no unible
- **WHEN** la reserva está `CANCELLED` o `COMPLETED`
- **THEN** el sistema responde 422 indicando que no admite nuevos participantes

#### Scenario: Reserva inexistente
- **WHEN** el `id` no corresponde a ninguna reserva
- **THEN** el sistema responde 404

#### Scenario: Última plaza en concurrencia
- **WHEN** dos usuarios se unen simultáneamente a la última plaza libre
- **THEN** exactamente uno recibe 200 y el otro 422 (la validación atómica evita superar el máximo)

### Requirement: Abandonar una partida
El sistema SHALL permitir a un participante no-owner abandonar una reserva a la que se unió, liberando su plaza. El owner NO SHALL poder abandonar (debe cancelar la reserva mediante el flujo de cancelación existente).

#### Scenario: Participante abandona
- **WHEN** un participante no-owner abandona una reserva activa en la que participa
- **THEN** el sistema lo elimina de `participants`, libera la plaza y responde con éxito

#### Scenario: El owner no puede abandonar
- **WHEN** el owner intenta abandonar su propia reserva
- **THEN** el sistema lo rechaza indicando que debe cancelar la reserva

### Requirement: Importe informativo al unirse (sin cobro online)
La UI de confirmar unión SHALL mostrar el importe "tu parte" (total de la reserva ÷ nº de participantes tras la unión) como referencia informativa, pero la unión NO SHALL crear un pago por participante ni cobrar online. El cobro compartido se difiere a la capability `pagos-redsys`.

#### Scenario: Mostrar tu parte sin cobrar
- **WHEN** el usuario abre la confirmación de unión a una partida
- **THEN** la UI muestra el importe que le correspondería, e informa de que el pago se realiza de forma presencial (sin pasarela)
