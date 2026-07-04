## ADDED Requirements

### Requirement: Marcar tramos reservables con el flag `creable`

El sistema SHALL incluir en cada `TramoDisponible` de la respuesta de `GET /api/reservas/disponibles` un campo booleano `creable` que indique si el tramo puede usarse para **crear** una nueva reserva. El valor SHALL ser `true` sii el tramo no tiene ninguna ocupación (`plazasLibres == max_participants`) y `false` cuando ya existe una reserva incompleta en esa franja (`plazasLibres < max_participants`), que solo admite unirse. El cálculo de `plazasLibres` y del conjunto de tramos devueltos NO cambia; el flag es aditivo y retrocompatible.

#### Scenario: Tramo totalmente libre es reservable
- **WHEN** un tramo no tiene ninguna reserva activa que lo solape (`plazasLibres == max_participants`)
- **THEN** el tramo aparece en `tramosDisponibles` con `creable: true`

#### Scenario: Tramo con reserva incompleta no es reservable
- **WHEN** un tramo tiene una reserva activa incompleta (`plazasLibres < max_participants`, con al menos una plaza libre)
- **THEN** el tramo aparece en `tramosDisponibles` con `creable: false`

#### Scenario: El campo `creable` está siempre presente
- **WHEN** el sistema devuelve cualquier tramo en `tramosDisponibles`
- **THEN** ese tramo incluye el campo `creable` con un valor booleano no nulo
