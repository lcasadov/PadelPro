## ADDED Requirements

### [AÑADIDO] Requirement: Estado operativo de la pista

**El sistema DEBE permitir al ADMIN cambiar el estado operativo de la pista (ACTIVA / MANTENIMIENTO) desde la configuración.**

#### Scenario: Pista en estado ACTIVA permite crear reservas

- **GIVEN** `system_config.pista_state = ACTIVA`
- **WHEN** un usuario envía `POST /api/reservas` con datos válidos
- **THEN** la reserva se crea exitosamente
- **AND** `GET /api/disponibilidad-pistas` devuelve slots disponibles

#### Scenario: Pista en estado MANTENIMIENTO bloquea nuevas reservas

- **GIVEN** `system_config.pista_state = MANTENIMIENTO`
- **WHEN** un usuario intenta `POST /api/reservas`
- **THEN** el sistema responde con HTTP `400`
- **AND** `GET /api/disponibilidad-pistas` devuelve estado MANTENIMIENTO sin slots

#### Scenario: ADMIN actualiza estado de la pista

- **GIVEN** un usuario autenticado con rol ADMIN
- **WHEN** envía `PATCH /api/admin/sistema/config` con `pistaState=MANTENIMIENTO`
- **THEN** la configuración se actualiza inmediatamente en BD
- **AND** posteriores requests ven el nuevo estado reflejado
- **AND** se registra en auditoría (Fase 2)
