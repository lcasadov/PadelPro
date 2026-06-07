## NEW Requirements

### Requirement: Estado operativo de la pista

**El sistema DEBE permitir al ADMIN cambiar el estado operativo de la pista (ACTIVA / MANTENIMIENTO) desde la configuración.**

#### Scenario: Pista en estado ACTIVA permite crear reservas

- **WHEN** `system_config.pista_state = ACTIVA`
- **THEN** los usuarios pueden crear nuevas reservas en `POST /api/reservas` sin restricción
- **AND** el endpoint `GET /api/disponibilidad-pistas` devuelve slots disponibles

#### Scenario: Pista en estado MANTENIMIENTO bloquea nuevas reservas

- **WHEN** `system_config.pista_state = MANTENIMIENTO`
- **THEN** si un usuario intenta `POST /api/reservas`, el sistema responde `400` con `{ "error": "BUSINESS_LOGIC_ERROR", "message": "Pista está en mantenimiento" }`
- **AND** el endpoint `GET /api/disponibilidad-pistas` devuelve estado MANTENIMIENTO sin slots disponibles

#### Scenario: ADMIN actualiza estado de la pista

- **WHEN** un ADMIN actualiza `pista_state` a MANTENIMIENTO vía `PATCH /api/admin/sistema/config`
- **THEN** la configuración se actualiza inmediatamente
- **AND** todas las nuevas requests ven el nuevo estado
- **AND** se inserta una entrada en `audit_log` con `action='CONFIG_UPDATED'`, `entity_type='SYSTEM_CONFIG'`, `details` contiene `"pista_state": "MANTENIMIENTO"`
