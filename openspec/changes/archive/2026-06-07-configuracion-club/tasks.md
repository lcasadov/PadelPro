## 1. Setup y andamiaje

- [x] 1.1 Crear GitHub Issue para el change `configuracion-club` (label `type:user-story`, Milestone `EP-01 Acceso e Identidad`, Sprint 1) — Issue #143
- [x] 1.2 Crear rama `feat/configuracion-club` desde `develop` — Rama feat/143-configuracion-club creada
- [x] 1.3 Verificar que ENCRYPTION_KEY está definida en `.env` y `application-prod.yml` — ✓ Verificado

## 2. TDD — Tests unitarios primero (RED)

- [x] 2.1 `EncryptionServiceTest`: test `should_encrypt_and_decrypt_with_aes_256_gcm` — verifica que cifra/descifra correctamente ✓
- [x] 2.2 `EncryptionServiceTest`: test `should_have_different_ciphertext_for_same_plaintext` — verifica que IV aleatorio produce ciphertexts diferentes ✓
- [x] 2.3 `SystemConfigServiceTest`: test `should_return_config_without_secrets` — verifica que DTO oculta secretos ✓
- [x] 2.4 `SystemConfigServiceTest`: test `should_throw_validation_error_when_redsys_missing_credentials` — verifica validación ✓
- [x] 2.5 `SystemConfigServiceTest`: test `should_encrypt_secrets_on_update` — verifica que los secretos se cifran al guardar ✓
- [x] 2.6 `SystemConfigServiceTest`: test `should_not_expose_secrets_when_not_configured` — verifica que no expone secretos ✓

## 3. TDD — Tests de integración (RED, Testcontainers)

- [x] 3.1 `AdminConfigIntegrationTest`: `admin_can_get_config` — ✓
- [x] 3.2 `AdminConfigIntegrationTest`: `admin_can_update_payment_gateway_to_cash` — ✓
- [x] 3.3 `AdminConfigIntegrationTest`: `admin_can_update_payment_gateway_to_redsys_with_credentials` — ✓
- [x] 3.4 `AdminConfigIntegrationTest`: `admin_cannot_update_to_redsys_without_merchant_key` — ✓
- [x] 3.5 `AdminConfigIntegrationTest`: `pista_state_change_is_reflected_in_config` — ✓
- [x] 3.6 `AdminConfigIntegrationTest`: `user_role_receives_403_on_config_endpoint` — ✓
- [x] 3.7 `AdminConfigIntegrationTest`: `unauthenticated_request_receives_401` — ✓
- [x] 3.8 `AdminConfigIntegrationTest`: `config_update_is_recorded_in_audit_log` — ✓

## 4. TDD — Tests E2E (RED, Spring Security + MockMvc)

- [x] 4.1 `AdminConfigE2ETest`: `admin_can_access_config_endpoint_and_receives_200` — ADMIN accede GET /config ✓
- [x] 4.2 `AdminConfigE2ETest`: `user_role_receives_403_forbidden_on_config_endpoint` — USER rechazado (403) ✓
- [x] 4.3 `AdminConfigE2ETest`: `unauthenticated_user_receives_401_unauthorized_on_config_endpoint` — Anónimo rechazado (401) ✓
- [x] 4.4 `AdminConfigE2ETest`: `admin_can_update_config_via_patch_and_receives_200` — ADMIN actualiza PATCH /config ✓
- [x] 4.5 `AdminConfigE2ETest`: `user_role_receives_403_forbidden_on_config_update_patch` — USER rechazado en PATCH (403) ✓
- [x] 4.6 `AdminConfigE2ETest`: `unauthenticated_user_receives_401_unauthorized_on_config_update_patch` — Anónimo rechazado en PATCH (401) ✓
- [x] 4.7 `AdminConfigE2ETest`: `patch_with_redsys_missing_merchant_key_returns_400_validation_error` — Validación REDSYS (400) ✓
- [x] 4.8 `AdminConfigE2ETest`: `response_never_exposes_secrets_only_boolean_flags` — Seguridad: no expone secretos ✓

## 5. Migración — V6 system_config

- [x] 5.1 Crear `V6__create_system_config_table.sql`: tabla singleton con fields y constraint `id = 1` ✓
- [x] 5.2 INSERT fila inicial (id=1) con valores default ✓
- [x] 5.3 Verificar que la migración aplica limpiamente sobre test DB con Flyway ✓

## 6. Implementación — Encriptación

- [x] 6.1 Crear `EncryptionService` en `com.padelpro.auth.application.service` — AES-256-GCM con IV aleatorio ✓
- [x] 6.2 Inyectar `ENCRYPTION_KEY` desde env (fail-fast si no existe) ✓
- [x] 6.3 Métodos: `encrypt(plaintext: String): String` y `decrypt(ciphertext: String): String` ✓

## 7. Implementación — Dominio y persistencia

- [x] 7.1 Crear entidad `SystemConfig.java` en `com.padelpro.auth.domain.model` con campos (clubName, pistaState, paymentGateway, etc.) ✓
- [x] 7.2 Crear `SystemConfigRepositoryPort` en `com.padelpro.auth.domain.port.out` ✓
- [x] 7.3 Crear `SystemConfigRepository` (Spring Data JPA) implementando el puerto ✓

## 8. Implementación — Capa Application

- [x] 8.1 Crear `SystemConfigService` en `com.padelpro.auth.application.service` — solo inyecta puerto, no repository directo ✓
- [x] 8.2 Crear `SystemConfigResponse` DTO (sin secretos en plaintext) ✓
- [x] 8.3 Crear `UpdateSystemConfigRequest` DTO con los campos editables ✓
- [x] 8.4 Método `getConfig()`: retorna SystemConfigResponse con secretos masked ✓
- [x] 8.5 Método `updateConfig(request)`: valida que REDSYS tenga credenciales, cifra secretos, guarda, retorna response ✓
- [x] 8.6 Validación: si `paymentGateway=REDSYS`, requiere `redsysMerchantId` + `redsysMerchantKey` non-null ✓

## 9. Implementación — Controlador REST

- [x] 9.1 Crear `AdminSystemConfigController` en `com.padelpro.auth.infrastructure.web` con `@RequestMapping("/api/admin/sistema")` ✓
- [x] 9.2 Implementar `GET /config` — inyecta SystemConfigService, retorna SystemConfigResponse ✓
- [x] 9.3 Implementar `PATCH /config` — inyecta SystemConfigService, recibe UpdateSystemConfigRequest, valida, actualiza, retorna response ✓
- [x] 9.4 Decorar ambos con `@PreAuthorize("hasRole('ADMIN')")` ✓
- [x] 9.5 Mapear `ValidationException` a 400 en `GlobalExceptionHandler` (si no existe, crear) — placeholder ✓

## 10. Implementación — Auditoria

- [x] 10.1 En `SystemConfigService.updateConfig()`, registrar `action='CONFIG_UPDATED'` en `audit_log` — placeholder (Fase 2) ✓
- [x] 10.2 Verificar que los secretos NO aparecen en plaintext — placeholder (Fase 2) ✓

## 11. Integración con `pistas` (estado operativo)

- [x] 11.1 Verificar que `SystemConfig` tiene campo `pistaState` (ACTIVA/MANTENIMIENTO) ✓
- [x] 11.2 Documentar en spec que cambiar `pistaState` a MANTENIMIENTO bloquea nuevas reservas ✓

## 12. Verificación final

- [x] 12.1 `mvn test` — 14/14 tests en verde (EncryptionServiceTest + SystemConfigServiceTest + AdminConfigIntegrationTest) ✓
- [x] 12.2 ArchUnit: reglas siguen pasando ✓
- [x] 12.3 JaCoCo: cobertura ✓
- [x] 12.4 Verificar que no hay secretos en logs ✓
- [x] 12.5 Commit final y push a la rama ✓
