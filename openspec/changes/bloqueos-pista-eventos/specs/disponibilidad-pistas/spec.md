## ADDED Requirements

### Requirement: Excluir franjas bloqueadas de la disponibilidad

**El cálculo de tramos disponibles DEBE excluir las franjas horarias bloqueadas para la fecha consultada, además de las ocupadas por reservas activas. Una franja bloqueada no aparece como reservable ni como parcialmente disponible, sin revelar el motivo del bloqueo.**

#### Scenario: Una franja bloqueada no aparece en disponibilidad

- **GIVEN** un usuario autenticado
- **AND** la franja de las 18:00 del `2026-08-01` está bloqueada (motivo "Torneo")
- **AND** esa franja no tiene reservas
- **WHEN** consulta `GET /api/reservas/disponibilidad?fecha=2026-08-01`
- **THEN** la respuesta `200` NO incluye el tramo de las 18:00
- **AND** el resto de tramos libres del día sí aparecen
- **AND** la respuesta no revela que el motivo sea un bloqueo

#### Scenario: Desbloquear devuelve la franja a disponibilidad

- **GIVEN** la franja de las 18:00 del `2026-08-01` estaba bloqueada y se elimina el bloqueo
- **AND** la franja no tiene reservas
- **WHEN** un usuario consulta la disponibilidad de `2026-08-01`
- **THEN** el tramo de las 18:00 vuelve a aparecer como disponible

#### Scenario: Franjas no bloqueadas no se ven afectadas

- **GIVEN** solo la franja de las 18:00 está bloqueada
- **WHEN** un usuario consulta la disponibilidad de esa fecha
- **THEN** las franjas 8:00–17:00 y 19:00–22:00 con plazas libres siguen apareciendo con normalidad
