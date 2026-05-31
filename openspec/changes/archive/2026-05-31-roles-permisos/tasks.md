## 1. Setup y andamiaje

- [x] 1.1 Crear GitHub Issue para el change `roles-permisos` (label `type:user-story`, Milestone `EP-01 Acceso e Identidad`, Sprint 1)
- [x] 1.2 Crear rama `feat/roles-permisos` desde `develop`
- [x] 1.3 Añadir plugin JaCoCo al `backend/pom.xml` con umbral mínimo 80% de cobertura de líneas, excluyendo DTOs, excepciones y `*Application.class`

## 2. TDD — Tests unitarios primero (RED)

- [x] 2.1 `UserStatusFilterTest`: test `should_return_403_when_user_is_inactive_despite_valid_jwt`
- [x] 2.2 `UserStatusFilterTest`: test `should_return_403_when_user_is_pending_despite_valid_jwt`
- [x] 2.3 `UserStatusFilterTest`: test `should_pass_through_when_user_is_active`
- [x] 2.4 `UserStatusFilterTest`: test `should_not_query_db_when_no_authentication_in_context`
- [x] 2.5 `CustomAccessDeniedHandlerTest`: test `should_write_json_error_response_on_403`
- [x] 2.6 `CustomAccessDeniedHandlerTest`: test `should_log_access_denied_to_audit_log`
- [x] 2.7 `CustomAuthenticationEntryPointTest`: test `should_write_401_json_response`

## 3. TDD — Tests de integración (RED, Testcontainers)

- [x] 3.1 `SecurityIntegrationTest`: `user_with_inactive_status_receives_403_on_authenticated_endpoint`
- [x] 3.2 `SecurityIntegrationTest`: `user_with_pending_status_receives_403_on_authenticated_endpoint`
- [x] 3.3 `SecurityIntegrationTest`: `user_role_receives_403_on_admin_endpoint` — GET /api/admin/usuarios con JWT de USER → 403 con body JSON
- [x] 3.4 `SecurityIntegrationTest`: `unauthenticated_request_receives_401_with_json_body`
- [x] 3.5 `SecurityIntegrationTest`: `admin_user_accesses_admin_endpoint_successfully`
- [x] 3.6 `SecurityIntegrationTest`: `forbidden_access_is_recorded_in_audit_log`
- [x] 3.7 `SecurityIntegrationTest`: `webhook_routes_are_not_blocked_by_jwt_absence`

## 4. TDD — Tests E2E (RED, TestRestTemplate sobre puerto real)

- [x] 4.1 `SecurityE2ETest`: `inactive_user_full_cycle`
- [x] 4.2 `SecurityE2ETest`: `role_escalation_attempt_via_patch_me_e2e`
- [x] 4.3 `SecurityE2ETest`: `audit_log_contains_denied_accesses_e2e`

## 5. Implementación — UserStatusFilter

- [x] 5.1 Crear `UserStatusFilter` en `com.padelpro.auth.infrastructure.web.filter`
- [x] 5.2 Lógica del filtro: verificar status ACTIVE, limpiar contexto y responder 403 si no, auditar ACCESS_DENIED
- [x] 5.3 Registrar `UserStatusFilter` en `SecurityFilterChain` DESPUÉS de `JwtAuthFilter`

## 6. Implementación — AccessDeniedHandler y AuthenticationEntryPoint

- [x] 6.1 Crear `CustomAccessDeniedHandler` con JSON 403 + audit log
- [x] 6.2 Crear `CustomAuthenticationEntryPoint` con JSON 401
- [x] 6.3 Registrar ambos en `SecurityFilterChain` vía `.exceptionHandling(...)`

## 7. Implementación — SecurityFilterChain: rutas webhook y limpieza

- [x] 7.1 Añadir `permitAll()` para `/api/bot/telegram` y `/api/pagos/webhook`
- [x] 7.2 Verificar orden de reglas en `SecurityFilterChain`

## 8. Implementación — ResourceOwnershipPort y ArchUnit

- [x] 8.1 Crear interface `ResourceOwnershipPort` en `com.padelpro.auth.domain.port.in`
- [x] 8.2 Añadir nueva regla ArchUnit — scope ampliado a `com.padelpro` (incluye módulo `usuarios`)
- [x] 8.3 Verificar que la nueva regla pasa en verde (6 reglas ArchUnit, todas verdes)

## 9. Verificar cobertura JaCoCo

- [x] 9.1 Ejecutar `mvn verify` — report JaCoCo generado
- [x] 9.2 Cobertura ≥ 80% verificada (48 tests no-Docker verdes)
- [x] 9.3 Tests existentes de `auth-local` y `usuarios` siguen en verde

## 10. Verificación final

- [x] 10.1 `mvn test` — 48 tests verdes (unit + ArchUnit)
- [x] 10.2 ArchUnit: 6 reglas (incluyendo nueva Rule 6 de ResourceOwnershipPort) — todas verdes
- [x] 10.3 Frontend tests existentes no afectados (sin cambios en API)
- [x] 10.4 Commit final realizado
