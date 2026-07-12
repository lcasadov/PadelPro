## 1. Tests de ramas — servicios (mayor déficit)

- [x] 1.1 `AdminReservaService` — nuevo `AdminReservaServiceTest`
- [x] 1.2 `SystemConfigService` — ampliado (cifrado/opcionales/validaciones)
- [x] 1.3 `ProcesarWebhookService` — ampliado (firmas/estados/idempotencia/error)
- [x] 1.4 `UserAdminService` — ampliado (ramas restantes)
- [x] 1.5 `CrearReservaService` — nuevo `CrearReservaServiceTest`
- [x] 1.6 `TelegramWebhookService` — ampliado
- [x] 1.7 `EmailNotificationService`, `PagoQueryService`, `ReservaMapper`, `SmtpNotificationAdapter` — ampliados/nuevos

## 2. Tests de ramas — dominio

- [x] 2.1 `Reservation` — nuevo `ReservationTest`
- [x] 2.2 `Payment`, `NotificationLog`, `SystemConfig`, `IdempotencyKey` — nuevos/ampliados (nota: `domain/model` está EXCLUIDO del gate JaCoCo, así que suben la cobertura raw pero no la gate-scoped)

## 3. Ramas inalcanzables

- [x] 3.1 No se forzaron tests artificiales; las ramas defensivas se dejaron sin cubrir (los excludes del gate ya cubren dto/exception/Application/domain.model)

## 4. Gate y verificación

- [x] 4.1 Medido con `mvn test jacoco:report` (unit-only): **ramas raw 46,73% → 74,06%** (+255); **gate-scoped 71,66%**
- [x] 4.2 Añadido a `jacoco-check` (pom.xml) `BRANCH COVEREDRATIO minimum 0.70` (bajo el suelo garantizado del CI de 71,66%); `LINE` se mantiene en 0.80
- [x] 4.3 `mvn -DskipITs test` en verde (14 ficheros de test añadidos/ampliados; sin regresión)
- [x] 4.4 PR — el % global de ramas del CI (unit+IT, ≥74%) se confirma en el informe JaCoCo del CI

## Notas
- El agente `test-runner` se cortó por límite de sesión tras subir la cobertura; el orquestador fijó el gate y cerró.
- **Ramas: unit-only 46,73%→74,06%.** El CI (unit+IT) partía de 65,2% y sube en consecuencia (≥74%). Gate `BRANCH ≥ 0.70` para blindar la mejora sin falsos rojos.
