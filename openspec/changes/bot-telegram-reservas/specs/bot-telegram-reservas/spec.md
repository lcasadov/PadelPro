## ADDED Requirements

### Requirement: [AÑADIDO] Despacho de comandos entrantes del bot

El sistema SHALL ampliar el procesamiento del webhook `POST /api/bot/telegram` para reconocer, además de `/vincular`, los comandos `/reservar`, `/misreservas`, `/cancelar`, `/confirmar` y `/ayuda`, despachando cada uno a su handler. La validación del secret `X-Telegram-Bot-Api-Secret-Token` (RN-TEL-01) SHALL ejecutarse antes de cualquier parseo. Un texto que no case con ningún comando conocido SHALL responder con el mensaje de `/ayuda`.

#### Scenario: Comando desconocido devuelve la ayuda
- **WHEN** un chat vinculado envía `/foobar`
- **THEN** el bot responde con el listado de comandos disponibles y su formato

#### Scenario: El secret inválido bloquea el despacho
- **WHEN** llega un update sin el header `X-Telegram-Bot-Api-Secret-Token` correcto
- **THEN** el sistema responde `403` y no despacha ningún comando

### Requirement: [AÑADIDO] Solo cuentas vinculadas pueden operar

Todo comando de negocio (`/reservar`, `/misreservas`, `/cancelar`, `/confirmar`) SHALL resolver el usuario por `telegram_chat_id` mediante `findByTelegramChatId`. Si el chat no está vinculado a ninguna cuenta, el sistema SHALL responder "vincula primero" y NO ejecutar ninguna operación de negocio. Cada operación SHALL actuar únicamente sobre reservas del propio usuario autenticado (`USER`).

#### Scenario: Chat no vinculado es rechazado
- **GIVEN** un chat sin `telegram_chat_id` asociado a ninguna cuenta
- **WHEN** envía `/misreservas`
- **THEN** el bot responde con instrucciones para vincular la cuenta y no consulta reservas

#### Scenario: Un usuario no puede operar sobre reservas ajenas
- **GIVEN** un usuario vinculado A y una reserva cuyo owner es el usuario B
- **WHEN** A envía `/cancelar <ref-de-la-reserva-de-B>`
- **THEN** el bot responde que la reserva no existe o no es suya y no inicia la cancelación

### Requirement: [AÑADIDO] Crear reserva con comando estructurado

El sistema SHALL permitir crear una reserva con `/reservar <fecha> <hora> [duración] [@jugador...]`, reutilizando `CrearReservaService.crear(ownerId, CrearReservaRequest, idempotencyKey)`. La reserva es por tramo (fecha + hora + duración); no hay selección de pista. El formato SHALL ser estricto: si el texto no casa, el sistema SHALL responder con un mensaje de ayuda que muestre el formato esperado y un ejemplo, sin crear nada. Las reglas de negocio (anti-solape, precio calculado en backend, límites) SHALL provenir del servicio existente, no reimplementarse en el bot.

#### Scenario: Creación exitosa
- **GIVEN** un usuario vinculado y un tramo libre el 2026-08-01 a las 18:00
- **WHEN** envía `/reservar 2026-08-01 18:00`
- **THEN** el bot crea la reserva vía `CrearReservaService`, responde con la referencia corta y el estado (pendiente de confirmación/pago), y registra `TELEGRAM_RESERVA_CREATED`

#### Scenario: Formato inválido devuelve ayuda
- **WHEN** un usuario vinculado envía `/reservar mañana por la tarde`
- **THEN** el bot responde con el formato esperado y un ejemplo, y no crea ninguna reserva

#### Scenario: Tramo no disponible reutiliza la validación de solape
- **GIVEN** un tramo ya ocupado que provocaría solape
- **WHEN** el usuario envía `/reservar` sobre ese tramo
- **THEN** el servicio rechaza la creación por solape y el bot traslada el error de forma legible

#### Scenario: Participantes @mención resueltos
- **GIVEN** un usuario vinculado B con handle conocido
- **WHEN** A envía `/reservar 2026-08-01 18:00 @B Juan`
- **THEN** B se añade como participante registrado y "Juan" como participante externo

### Requirement: [AÑADIDO] Confirmar reserva con OTP por bot

El sistema SHALL permitir confirmar una reserva pendiente con `/confirmar <ref> <otp>`, validando un OTP **ligado a la reserva referenciada** (`otp_codes.reservation_id`, D-4) vía `OtpService` (RN-AUTH-07: 6 dígitos, TTL 10 min, un solo uso, máx. 3 intentos). La operación (confirmar vs. cancelar) SHALL determinarse por el tipo del OTP activo de ESA reserva (`RESERVATION_CONFIRM` vs `CANCELLATION_CONFIRM`), no por precedencia global de tipo. La confirmación SHALL aplicarse solo si la reserva es del usuario y está en un estado confirmable.

#### Scenario: Confirmación exitosa
- **GIVEN** una reserva pendiente del usuario y un OTP `RESERVATION_CONFIRM` válido ligado a esa reserva
- **WHEN** el usuario envía `/confirmar <ref> <otp>`
- **THEN** el OTP se consume, la reserva pasa a confirmada y se registra `TELEGRAM_RESERVA_CONFIRMED`

#### Scenario: OTP incorrecto no confirma
- **WHEN** el usuario envía `/confirmar <ref>` con un OTP inválido
- **THEN** el bot informa del error, incrementa el conteo de intentos y no cambia el estado de la reserva

#### Scenario: Confirmar X nunca cancela X aunque haya una cancelación pendiente de Y
- **GIVEN** el usuario tiene la reserva X pendiente de confirmar (OTP `RESERVATION_CONFIRM` ligado a X) y una cancelación en curso de otra reserva Y (OTP `CANCELLATION_CONFIRM` ligado a Y)
- **WHEN** el usuario envía `/confirmar X <otp-de-X>`
- **THEN** el bot confirma X (nunca la cancela) y no consume el intento del OTP de Y

### Requirement: [AÑADIDO] Cancelar reserva con OTP por bot

El sistema SHALL permitir cancelar una reserva propia con `/cancelar <ref>`, emitiendo un OTP `CANCELLATION_CONFIRM` y aplicando la cancelación vía `CancelarReservaService.cancelar(id, userId, admin=false)` tras la confirmación. La cancelación SHALL respetar las reglas del servicio existente (plazo de cancelación, estado).

#### Scenario: Cancelación en dos pasos
- **GIVEN** una reserva cancelable del usuario
- **WHEN** el usuario envía `/cancelar <ref>` y luego confirma con el OTP recibido
- **THEN** `CancelarReservaService` cancela la reserva y se registra `TELEGRAM_RESERVA_CANCELLED`

#### Scenario: Fuera de plazo de cancelación
- **GIVEN** una reserva cuyo plazo de cancelación ya venció
- **WHEN** el usuario intenta `/cancelar <ref>`
- **THEN** el servicio rechaza la cancelación y el bot traslada el motivo

### Requirement: [AÑADIDO] Listar mis reservas con referencia corta

El sistema SHALL responder a `/misreservas` con las reservas del usuario (`ReservaQueryService.listForUser`), mostrando por cada una una **referencia corta** estable (índice o prefijo del UUID) utilizable en `/cancelar` y `/confirmar`, junto a fecha, hora y estado.

#### Scenario: Listado con referencias
- **GIVEN** un usuario vinculado con dos reservas
- **WHEN** envía `/misreservas`
- **THEN** el bot lista ambas con su referencia corta, fecha/hora y estado

#### Scenario: Sin reservas
- **WHEN** un usuario vinculado sin reservas envía `/misreservas`
- **THEN** el bot responde que no tiene reservas y sugiere `/reservar`

### Requirement: [AÑADIDO] Ayuda y auditoría

El comando `/ayuda` SHALL enumerar los comandos y su formato. Cada operación de negocio y cada rechazo SHALL registrarse en auditoría (`TELEGRAM_RESERVA_CREATED`, `TELEGRAM_RESERVA_CONFIRMED`, `TELEGRAM_RESERVA_CANCELLED`, `TELEGRAM_COMMAND_REJECTED`), sin volcar OTP en claro (RN-RGPD-04).

#### Scenario: Ayuda lista comandos
- **WHEN** un usuario envía `/ayuda`
- **THEN** el bot responde con `/reservar`, `/misreservas`, `/confirmar`, `/cancelar` y su formato

#### Scenario: Rechazo auditado sin OTP en claro
- **WHEN** un comando es rechazado por formato o permisos
- **THEN** se registra `TELEGRAM_COMMAND_REJECTED` y ningún log contiene el OTP en claro
