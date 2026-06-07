# Plan de ejecución — Configuración centralizada de club (configuracion-club)

**Sesión:** 2026-06-07  
**Objetivo:** Implementar gestión centralizada de configuración de club con encriptación de secretos (ENCRYPTION_KEY, Telegram Bot token, credenciales Redsys).

---

## Fase 1 — Setup (✅ COMPLETADA)

- [x] Crear GitHub Issue #143
- [x] Crear rama `feat/143-configuracion-club`
- [x] Verificar ENCRYPTION_KEY en `.env`
- [x] Validar OpenSpec (`openspec/changes/configuracion-club/`)

---

## Fase 2 — Implementación (delegada a backend-architect)

### Bloque A: Tests unitarios (RED)
- [ ] 2.1 `EncryptionServiceTest` — cifra/descifra con AES-256-GCM
- [ ] 2.2 `EncryptionServiceTest` — IV aleatorio genera ciphertexts diferentes
- [ ] 2.3 `SystemConfigServiceTest` — DTO oculta secretos
- [ ] 2.4 `SystemConfigServiceTest` — validación de credenciales Redsys
- [ ] 2.5 `SystemConfigServiceTest` — encriptación de secretos en update
- [ ] 2.6 `SystemConfigServiceTest` — desencriptación de secretos en read

### Bloque B: Tests integración (RED, Testcontainers)
- [ ] 3.1 `AdminConfigIntegrationTest` — GET /api/admin/sistema/config (ADMIN)
- [ ] 3.2 `AdminConfigIntegrationTest` — PATCH payment_gateway a CASH
- [ ] 3.3 `AdminConfigIntegrationTest` — PATCH payment_gateway a REDSYS con credenciales
- [ ] 3.4 `AdminConfigIntegrationTest` — PATCH sin merchant_key → 400 VALIDATION_ERROR
- [ ] 3.5 `AdminConfigIntegrationTest` — cambiar pista_state a MANTENIMIENTO
- [ ] 3.6 `AdminConfigIntegrationTest` — USER role → 403 ACCESS_DENIED
- [ ] 3.7 `AdminConfigIntegrationTest` — sin JWT → 401 AUTH_REQUIRED
- [ ] 3.8 `AdminConfigIntegrationTest` — CONFIG_UPDATED en audit_log

### Bloque C: Tests E2E (RED, TestRestTemplate)
- [ ] 4.1 `AdminConfigE2ETest` — workflow admin: login → GET → PATCH → verifica BD

### Bloque D: Migración (Flyway V6)
- [ ] 5.1 Crear `V6__create_system_config_table.sql`
- [ ] 5.2 INSERT fila inicial con defaults
- [ ] 5.3 Verificar migración en test DB

### Bloque E: Encriptación
- [ ] 6.1 `EncryptionService` (AES-256-GCM, IV aleatorio)
- [ ] 6.2 Inyectar ENCRYPTION_KEY desde env (fail-fast si no existe)
- [ ] 6.3 Métodos: encrypt(plaintext) / decrypt(ciphertext)

### Bloque F: Dominio y persistencia
- [ ] 7.1 Entity `SystemConfig` con todos los campos
- [ ] 7.2 `SystemConfigRepositoryPort` (puertos)
- [ ] 7.3 `SystemConfigRepository` (Spring Data JPA)

### Bloque G: Capa Application
- [ ] 8.1 `SystemConfigService` (inyecta puerto, no repository)
- [ ] 8.2 `SystemConfigResponse` DTO (sin secretos)
- [ ] 8.3 `UpdateSystemConfigRequest` DTO
- [ ] 8.4 Método `getConfig()` — retorna response con secretos masked
- [ ] 8.5 Método `updateConfig(request)` — cifra, guarda, retorna response
- [ ] 8.6 Validación: REDSYS requiere credenciales completas

### Bloque H: Controlador REST
- [ ] 9.1 `AdminSystemConfigController` (/api/admin/sistema)
- [ ] 9.2 Endpoint GET /config (inyecta service, retorna response)
- [ ] 9.3 Endpoint PATCH /config (inyecta service, valida, actualiza)
- [ ] 9.4 Decorar con @PreAuthorize("hasRole('ADMIN')")
- [ ] 9.5 Mapear ValidationException a 400 en GlobalExceptionHandler

### Bloque I: Auditoría
- [ ] 10.1 En `updateConfig()`, registrar `CONFIG_UPDATED` en audit_log
- [ ] 10.2 Secretos masked en campo `details` ("***REDACTED***")

### Bloque J: Integración con pistas
- [ ] 11.1 SystemConfig tiene campo `pistaState` (ACTIVA/MANTENIMIENTO)
- [ ] 11.2 Documentar que MANTENIMIENTO bloquea nuevas reservas (futuro)

### Bloque K: Verificación final
- [ ] 12.1 `mvn test` — todos en verde
- [ ] 12.2 ArchUnit — reglas siguen pasando
- [ ] 12.3 JaCoCo — cobertura ≥ 80%
- [ ] 12.4 Grep secretos en logs — vacío
- [ ] 12.5 Commit final + push

---

## Fase 3 — QA (post-implementación)

- [ ] `test-runner` — ejecutar suite completa, reportar cobertura
- [ ] `verification-specialist` — verificación adversarial (boundary, auth, injection)
- [ ] `reality-checker` — validación user journeys end-to-end

---

## Fase 4 — PR y merge

- [ ] Crear PR de `feat/143-configuracion-club` a `develop`
- [ ] CI pasa (tests, lint, build)
- [ ] Code review aprobada
- [ ] Mergear y cerrar Issue #143

---

## Fase 5 — Cierre

- [ ] Actualizar `docs/plan/plan.md` con tiempos reales
- [ ] Ejecutar `/opsx:archive configuracion-club` para sincronizar specs y archivar change
- [ ] Notificar que feature está lista para Wave 2

---

## Secuencia de paralelismo

**En paralelo (Bloque A, B, C no dependen entre sí, solo de que exista la rama):**
- Bloque A: Tests unitarios (establece contratos de API)
- Bloque B: Tests integración (depende de tests unitarios ✓, pero se puede escribir en paralelo)
- Bloque C: Tests E2E (depende de todo el stack)

**Secuencia estricta:**
1. Bloques A+B (tests + migración) — RED
2. Bloques D+E+F+G+H+I (implementación) — GREEN
3. Bloque J (verificación final) — REFACTOR
4. Bloque K (tests finales en verde)

---

## Riesgos identificados

| Riesgo | Impacto | Mitigación |
|--------|---------|-----------|
| ENCRYPTION_KEY no definida | BLOQUEANTE | Verificado en Phase 1 ✓ |
| ArchUnit rechaza SecurityModule | MEDIO | Validar early en tests unitarios |
| JaCoCo < 80% | BAJO | Revisar cobertura antes de 12.2 |
| Conflictos con auditoria (Wave 1) | BAJO | Auditoria mergeada antes (PR #150) |

---

## Fuera de alcance

- Interfaz web (Fase 2 — `administracion-club`)
- Auditoría exhaustiva (puede mejorarse en el futuro)
- Validación de credenciales Redsys contra servidor real (mock en tests)

---

**¿Apruebas este plan?**

Responde "sí" o "aprobado" para proceder a Phase 2 (delegación a `backend-architect`), o indica cambios si los hay.
