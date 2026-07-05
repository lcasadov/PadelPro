## ADDED Requirements

### Requirement: Búsqueda de usuarios registrados para selección de compañero
El sistema SHALL exponer un endpoint autenticado para buscar usuarios registrados por nombre o email, devolviendo un conjunto mínimo de datos (id y nombre para mostrar) suficiente para alimentar el selector de compañero registrado en la UI de reserva. Los resultados SHALL estar limitados en tamaño (paginación o límite) para evitar exponer el directorio completo de una sola vez.

#### Scenario: Buscar por término coincidente
- **WHEN** un usuario autenticado busca por un término que coincide con el nombre o email de socios existentes
- **THEN** el endpoint devuelve una lista acotada de usuarios con al menos `id` y nombre para mostrar

#### Scenario: Sin coincidencias
- **WHEN** el término de búsqueda no coincide con ningún usuario
- **THEN** el endpoint devuelve una lista vacía y código 200

#### Scenario: Requiere autenticación
- **WHEN** se llama al endpoint sin credenciales válidas
- **THEN** el endpoint responde 401 y no devuelve datos de usuarios

#### Scenario: No expone datos sensibles
- **WHEN** el endpoint devuelve resultados
- **THEN** cada resultado incluye únicamente los campos mínimos para el selector (id, nombre) y no datos sensibles (p. ej. hash de contraseña, roles internos)
