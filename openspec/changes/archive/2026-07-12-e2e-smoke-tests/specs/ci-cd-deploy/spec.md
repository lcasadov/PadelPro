## ADDED Requirements

### Requirement: Suite E2E smoke del journey crítico en el pipeline

El pipeline DEBE (MUST) ejecutar una suite de tests **end-to-end** con un navegador real (Playwright/Chromium) que ejercite el journey crítico **iniciar sesión → crear reserva** contra el stack completo levantado con `docker compose` (frontend build + backend + base de datos reales, **sin mocks**). El job E2E DEBE (MUST) correr después de `build-and-test`, tanto en `pull_request` como en `push`, y su fallo DEBE (MUST) bloquear el despliegue.

#### Scenario: Journey crítico en verde
- **WHEN** se abre un `pull_request` o se hace `push` y el stack levanta correctamente
- **THEN** el job `e2e` inicia sesión, crea una reserva a través de la UI real y verifica la pantalla de confirmación; termina en verde

#### Scenario: Fallo del journey bloquea el deploy
- **WHEN** el smoke E2E falla (p. ej. el flujo de crear reserva se rompe, como en el estilo del bug #201)
- **THEN** el pipeline queda en rojo y el job `deploy` NO se ejecuta

#### Scenario: Evidencia en caso de fallo
- **WHEN** un test E2E falla en CI
- **THEN** se publican como artefactos la traza de Playwright y/o capturas para diagnóstico

### Requirement: Ejecución del E2E contra el stack real sin mocks

La suite E2E DEBE (MUST) apuntar al frontend servido como build de producción y al backend real (no debe usar MSW ni dobles de red), de modo que reproduzca el comportamiento observable por un usuario y detecte regresiones de integración que los tests de componente con mocks no pueden ver.

#### Scenario: Sin dobles de red
- **WHEN** se ejecuta la suite E2E
- **THEN** las peticiones del navegador llegan al backend real y a la base de datos, sin interceptores ni respuestas simuladas
