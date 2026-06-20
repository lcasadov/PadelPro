## ADDED Requirements

### Requirement: Configuración del precio por hora de la pista

El sistema SHALL almacenar `price_per_hour` (`NUMERIC(12,2)`, mayor que 0) en `system_config` y SHALL permitir al ADMIN consultarlo y modificarlo. Este valor es la fuente única para el cálculo del importe de las reservas (RN-RES-03).

#### Scenario: ADMIN consulta la configuración incluyendo el precio
- **WHEN** un ADMIN envía `GET /api/admin/sistema/config`
- **THEN** la respuesta incluye `pricePerHour` con el valor vigente

#### Scenario: ADMIN actualiza el precio por hora
- **WHEN** un ADMIN envía `PATCH /api/admin/sistema/config` con `pricePerHour: 18.00`
- **THEN** el sistema responde 200 y persiste el nuevo precio
- **AND** las reservas creadas a partir de ese momento usan el nuevo precio, sin afectar a las existentes (US-024)

#### Scenario: Precio inválido rechazado
- **WHEN** un ADMIN envía `PATCH /api/admin/sistema/config` con `pricePerHour: 0` o un valor negativo
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`

#### Scenario: Valor por defecto en la fila singleton
- **WHEN** se aplica la migración que añade `price_per_hour` sobre la fila de configuración existente
- **THEN** la fila toma el valor por defecto `15.00` sin requerir intervención manual

### Requirement: Configuración del plazo de cancelación

El sistema SHALL almacenar `cancellation_deadline_hours` (`INTEGER`, mayor o igual a 0, por defecto 2) en `system_config` y SHALL permitir al ADMIN consultarlo y modificarlo. Este valor determina la antelación mínima para cancelar una reserva con reembolso (RN-RES-04).

#### Scenario: ADMIN consulta el plazo de cancelación
- **WHEN** un ADMIN envía `GET /api/admin/sistema/config`
- **THEN** la respuesta incluye `cancellationDeadlineHours` con el valor vigente

#### Scenario: ADMIN actualiza el plazo de cancelación
- **WHEN** un ADMIN envía `PATCH /api/admin/sistema/config` con `cancellationDeadlineHours: 24`
- **THEN** el sistema responde 200 y persiste el nuevo plazo

#### Scenario: Plazo inválido rechazado
- **WHEN** un ADMIN envía `PATCH /api/admin/sistema/config` con `cancellationDeadlineHours` negativo
- **THEN** el sistema responde 400 con `code: "VALIDATION_ERROR"`
