## 1. Setup y andamiaje

- [ ] 1.1 Crear GitHub Issue para el change `auditoria` (label `type:user-story`, Milestone `EP-01 Acceso e Identidad`, Sprint 5)
- [ ] 1.2 Crear rama `feat/<issue>-auditoria` desde `develop`
- [ ] 1.3 Verificar que V3 migration existe y que `AuditLog`, `AuditLogRepository`, `AuditLogRepositoryPort` están en verde

## 2. TDD — Tests unitarios primero (RED)

- [ ] 2.1 `AuditLogServiceTest`: test `should_return_page_of_all_logs_when_no_filter` — verifica paginación sin filtros
- [ ] 2.2 `AuditLogServiceTest`: test `should_filter_by_action` — verifica que solo se retornan entradas con el `action` indicado
- [ ] 2.3 `AuditLogServiceTest`: test `should_filter_by_userId` — verifica filtro por `userId`
- [ ] 2.4 `AuditLogServiceTest`: test `should_filter_by_date_range` — verifica filtro `from`/`to`
- [ ] 2.5 `AuditLogServiceTest`: test `should_throw_when_size_exceeds_100` — verifica que `size > 100` lanza `ValidationException`
- [ ] 2.6 `AuditLogServiceTest`: test `should_map_audit_log_to_entry_response_with_user_email` — verifica mapping cuando user no es null
- [ ] 2.7 `AuditLogServiceTest`: test `should_map_audit_log_to_entry_response_with_null_user` — verifica mapping cuando user es null

## 3. TDD — Tests de integración (RED, Testcontainers)

- [ ] 3.1 `AdminAuditIntegrationTest`: `admin_can_list_audit_log_paginated` — `GET /api/admin/audit` con ADMIN JWT → 200 + `PagedAuditLogResponse`
- [ ] 3.2 `AdminAuditIntegrationTest`: `admin_can_filter_by_action` — `GET /api/admin/audit?action=ACCESS_DENIED` → solo entradas con esa acción
- [ ] 3.3 `AdminAuditIntegrationTest`: `admin_can_filter_by_user_id` — `GET /api/admin/audit?userId={id}` → solo entradas de ese usuario
- [ ] 3.4 `AdminAuditIntegrationTest`: `admin_can_filter_by_date_range` — `GET /api/admin/audit?from=...&to=...` → entradas dentro del rango
- [ ] 3.5 `AdminAuditIntegrationTest`: `size_exceeding_100_returns_400_validation_error` — `GET /api/admin/audit?size=500` → 400 con `VALIDATION_ERROR`
- [ ] 3.6 `AdminAuditIntegrationTest`: `user_role_receives_403_on_audit_endpoint` — USER JWT → 403 `ACCESS_DENIED`
- [ ] 3.7 `AdminAuditIntegrationTest`: `unauthenticated_request_receives_401_on_audit_endpoint` — sin JWT → 401 `AUTH_REQUIRED`
- [ ] 3.8 `AdminAuditIntegrationTest`: `response_does_not_contain_sensitive_fields` — ninguna entrada retornada tiene `password`, `token`, `otp` o `secret` en `details`

## 4. TDD — Tests E2E (RED, TestRestTemplate)

- [ ] 4.1 `AdminAuditE2ETest`: `admin_can_find_login_event_in_audit_log_after_login` — hace login real → verifica que `USER_LOGIN_SUCCESS` aparece en `GET /api/admin/audit?action=USER_LOGIN_SUCCESS`
- [ ] 4.2 `AdminAuditE2ETest`: `admin_can_find_access_denied_event_after_unauthorized_attempt` — USER intenta acceder a admin → ACCESS_DENIED aparece en el log

## 5. Migración — V5 audit_log evolution

- [ ] 5.1 Crear `V5__evolve_audit_log.sql`: `ALTER COLUMN action TYPE VARCHAR(100)`, `ADD COLUMN entity_type`, `ADD COLUMN entity_id`, `ADD COLUMN channel DEFAULT 'WEB'`
- [ ] 5.2 Verificar que la migración aplica limpiamente sobre la BD de test con Flyway

## 6. Implementación — Dominio y puertos

- [ ] 6.1 Actualizar `AuditLog.java`: añadir campos `entityType`, `entityId`, `channel` con sus getters
- [ ] 6.2 Crear `AuditLogFilter.java` (record en `application.dto`): campos `action`, `userId`, `entityType`, `from`, `to`
- [ ] 6.3 Añadir método `Page<AuditLog> findFiltered(AuditLogFilter filter, Pageable pageable)` a `AuditLogRepositoryPort`

## 7. Implementación — Infraestructura persistencia

- [ ] 7.1 Añadir `extends JpaSpecificationExecutor<AuditLog>` a `AuditLogRepository`
- [ ] 7.2 Implementar `AuditLogSpecification` con predicados por `action`, `userId`, `entityType`, `from`, `to` (null-safe)
- [ ] 7.3 Implementar el método `findFiltered` en `AuditLogRepository` usando `findAll(Specification, Pageable)`

## 8. Implementación — Capa Application

- [ ] 8.1 Crear `AuditLogEntryResponse.java` (record en `application.dto`): `id`, `action`, `userId`, `userEmail`, `ipAddress`, `details`, `entityType`, `entityId`, `channel`, `createdAt`
- [ ] 8.2 Crear `PagedAuditLogResponse.java` (record en `application.dto`): `content`, `page`, `size`, `totalElements`, `totalPages`
- [ ] 8.3 Crear `AuditLogService.java` en `com.padelpro.auth.application.service`: método `findAuditLogs(AuditLogFilter, Pageable)` → `PagedAuditLogResponse`
- [ ] 8.4 Añadir validación `size ≤ 100` en `AuditLogService`, lanzar `ValidationException` si se supera

## 9. Implementación — Controlador REST

- [ ] 9.1 Crear `AdminAuditController.java` en `com.padelpro.auth.infrastructure.web` con `@RequestMapping("/api/admin/audit")`
- [ ] 9.2 Implementar `GET /` con parámetros opcionales `action`, `userId`, `entityType`, `from`, `to`, `page`, `size`, `sort`
- [ ] 9.3 Mapear `ValidationException` a 400 en `GlobalExceptionHandler` (reusar si ya existe, añadir si no)
- [ ] 9.4 Verificar que el endpoint responde correctamente con el token de ADMIN

## 10. Verificación final

- [ ] 10.1 `mvn test` — todos los tests en verde (unit + integración + E2E existentes no rotos)
- [ ] 10.2 ArchUnit: 6 reglas siguen en verde (AdminAuditController en `infrastructure.web`, AuditLogService no depende de infra directamente)
- [ ] 10.3 JaCoCo: cobertura ≥ 80% (AuditLogService y AdminAuditController cubiertos)
- [ ] 10.4 Commit final y push a la rama
