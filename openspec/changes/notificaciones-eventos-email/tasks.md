# Notificaciones por email de eventos (fase-1, Wave 4B)

> Orden TDD estricto (Red → Green → Refactor). Reutiliza `NotificationPort`/`SmtpNotificationAdapter` existentes. Envío asíncrono post-commit, tolerante a fallos (RN-NOT-01/02). Telegram DIFERIDO a `auth-otp-telegram`.

## 1. Persistencia — notification_log

- [x] 1.1 **[Red]** Test del repositorio/puerto de `notification_log`: guardar una entrada (PENDING→SENT/FAILED), buscar FAILED con `attempts < 3` para el job
- [x] 1.2 **[Green]** Migración Flyway `Vn__create_notification_log.sql` (campos de la spec + `attempts`, índices por `status` y `related_entity`) + entidad + puerto/adaptador de persistencia

## 2. Servicio de notificación + registro

- [x] 2.1 **[Red]** Test: enviar una notificación registra `PENDING`, luego `SENT` si el `NotificationPort` tiene éxito, o `FAILED`+`error_message` si lanza; nunca propaga la excepción; `message`/`recipient` sin datos sensibles (RN-RGPD-04)
- [x] 2.2 **[Green]** Servicio de notificación que orquesta: registra entrada, envía por `NotificationPort` (@Async), actualiza estado. Plantillas de confirmación, cancelación y recibo (patrón `WelcomeEmailTemplate`)

## 3. Disparadores en reserva y pago (D1)

- [x] 3.1 **[Red]** Test: al pasar una reserva a `CONFIRMED` se dispara el email de confirmación (post-commit) y queda registro; SMTP caído → reserva sigue CONFIRMED + registro FAILED
- [x] 3.2 **[Green]** Enganche de confirmación en el servicio de reserva (evento de dominio + `@TransactionalEventListener(AFTER_COMMIT)`)
- [x] 3.3 **[Red]** Test: al pasar una reserva a `CANCELLED` se envía email de cancelación al titular (con motivo si existe); no se notifica a participantes no-titulares
- [x] 3.4 **[Green]** Enganche de cancelación en `CancelarReservaService`
- [x] 3.5 **[Red]** Test: al pasar un `Payment` a `PAID` (webhook Redsys y efectivo ADMIN) se envía recibo al titular con importe/fecha/referencia
- [x] 3.6 **[Green]** Enganche de recibo en `ProcesarWebhookService` y `RegistrarPagoEfectivoService`

## 4. Reintentos (Req 3, D3)

- [x] 4.1 **[Red]** Test: el job recoge entradas `FAILED` con `attempts<3` y reintenta; éxito → `SENT`; 3 fallos → queda `FAILED` sin más reintentos; respeta el backoff
- [x] 4.2 **[Green]** Job `@Scheduled` de reintentos con backoff y tope 3 (habilitar `@EnableScheduling` si no está)

## 5. QA

- [x] 5.1 **[Refactor]** Limpieza manteniendo verde
- [x] 5.2 `verification-specialist`: build + tests (backend maven) + lint; probes de tolerancia a fallo (SMTP caído no revierte negocio), no-PII en log, tope de reintentos — PARTIAL: compila, 0 fallos lógicos, todos los unit verdes; los IT Testcontainers no ejecutables por incompatibilidad Docker 29 ↔ docker-java de la máquina (afecta a todo el suite del proyecto), diferidos a CI Linux
- [x] 5.3 `security-auditor`: no exposición de datos sensibles en `notification_log`/logs (RN-RGPD-04), no envío a usuarios anonimizados — 1 MEDIA + 2 BAJA corregidos (commits 810891d filtro INACTIVE RGPD, 7cc9393 no-PII en logs, e7a34d5 paginar findRetriable)
- [x] 5.4 `reality-checker`: journey (confirmar reserva / cancelar / pagar → llega email + registro) — READY (A-); 6 journeys verificados por código + unit; gap de cobertura de PaymentPaidEmailEvent cerrado (commit 419d2d1); E2E live diferido a CI
- [x] 5.5 Verificar que no hay endpoints REST nuevos (event-driven) y que la doc refleja el alcance email (Telegram diferido) — confirmado sin controllers nuevos
