## Why

La cobertura de **ramas** del backend está en **65,2 %** (608/933 en el informe JaCoCo del CI), muy por debajo de la de líneas (89,3 %). El gate del proyecto solo exige líneas ≥80 %, así que muchas ramas (validaciones, guards, casos límite) quedan sin ejercitar — justo donde se esconden los bugs. Subir la cobertura de ramas endurece la red de seguridad.

## What Changes

- Se añaden tests unitarios que ejercitan las **ramas sin cubrir** de las clases con mayor déficit (por nº de ramas perdidas en el informe JaCoCo):
  - `AdminReservaService` (20/20 ramas sin cubrir), `SystemConfigService` (21), dominio `Reservation` (17), `ProcesarWebhookService` (15), `NotificationLog` (14), `Payment` (14), `SystemConfig` (14), `UserAdminService` (12), `CrearReservaService` (11), `IdempotencyKey` (11), `TelegramWebhookService` (10), `EmailNotificationService` (9), `ReservaMapper` (8), `PagoQueryService` (8), `SmtpNotificationAdapter` (8)…
- Objetivo: **cobertura global de ramas ≥ 80 %** (o lo más cerca posible), sin bajar líneas.
- Se **añade un gate de ramas** a `jacoco-check` (`BRANCH COVEREDRATIO`) al nivel alcanzado (con un margen prudente) para que no vuelva a caer.

## Capabilities

### New Capabilities
<!-- Ninguna: mejora de calidad transversal (testing). -->

### Modified Capabilities
<!-- Sin cambios de requisitos de producto. El gate de cobertura pertenece a la capability transversal ci-cd-deploy (Requisito 2). -->

## Impact

- **Testing (`backend/src/test`):** nuevos tests unitarios (sin BD; mocks / dominio puro). Sin cambios de código de producto.
- **`backend/pom.xml`:** gate JaCoCo ampliado con un límite de ramas.
- **Fase del producto:** fase-1 (calidad).

## Fuera de alcance

- Refactor de código de producto para "hacerlo más testeable" (solo se añaden tests; si alguna rama es inalcanzable/defensiva, se documenta o excluye justificadamente).
- Cobertura de ramas de clases de infraestructura triviales (config/wiring) donde no aporta valor.
