## 1. Puertos de escritura cross-módulo

- [x] 1.1 Puerto/método para **revocar todos los `refresh_tokens`** de un `user_id` (módulo auth): `revoked=true`
- [x] 1.2 Puerto/método para **invalidar todos los `otp_codes` activos** de un `user_id` (módulo otp): `used=true` (reutilizar si ya existe de auth-otp-telegram)
- [x] 1.3 Puerto/método para **anonimizar `participants`** de un `user_id`: `external_name='ANONIMIZADO'`, `external_phone=NULL`

## 2. Anonimización en el servicio

- [x] 2.1 Reescribir `UserAdminService.deactivateUser` → anonimización atómica (`@Transactional`): sobrescribe `users` (first_name/last_name/email/phone/telegram_*), `status=INACTIVE`, mantiene el guard de auto-borrado (RN-AUTH-05)
- [x] 2.2 Invocar los 3 puertos (revocar refresh_tokens, invalidar otp_codes, anonimizar participants) en la misma transacción
- [x] 2.3 Auditar `USER_ANONYMIZED` (ejecutor=admin, entity USER/{id}); añadir la acción a `AuditActions` (sustituye `USER_DEACTIVATED` en este flujo)
- [x] 2.4 No loggear datos personales (RN-RGPD-04); idempotente ante doble anonimización (D4)

## 3. Endpoint

- [x] 3.1 `DELETE /api/admin/usuarios/{id}` sigue devolviendo **204**; verificar que llama a la anonimización

## 4. Tests (cobertura ≥80% del código nuevo)

- [x] 4.1 ADMIN anonimiza cuenta → campos sobrescritos, INACTIVE, refresh_tokens revocados, otp_codes invalidados, participants anonimizados, `USER_ANONYMIZED` en audit, 204
- [x] 4.2 Guard de auto-anonimización (admin sobre sí mismo) → excepción, sin cambios
- [x] 4.3 `reservations`/`payments`/audit histórico NO se modifican
- [x] 4.4 Idempotencia: anonimizar un usuario ya anonimizado no falla
- [x] 4.5 Usuario inexistente → 404/excepción controlada

## 5. QA y cierre

- [x] 5.1 Suite unitaria pura en verde (excl. *IntegrationTest/*E2ETest, que fallan solo por Docker 29 en local; CI Linux los valida). Tests de `deactivateUser` reescritos.
- [x] 5.2 Auto-revisión + `openspec/plan.md` actualizado (exportaciones-rgpd ✅ v1.0)
- [x] 5.3 PR

## Notas de implementación
- `DELETE /api/admin/usuarios/{id}` ahora **anonimiza irreversiblemente** (era soft-delete): sobrescribe campos personales + INACTIVE + revoca refresh_tokens + invalida otp_codes + anonimiza participants + `USER_ANONYMIZED`. 204. Atómico.
- **Conflicto spec↔esquema en `participants`** (documentado): las filas `user_id={id}` son participaciones *registradas* (CHECK `chk_part_user_or_external`: user_id XOR external_name), sin datos externos propios; sus datos personales viven en `users` (ya anonimizado). Se conserva `user_id` (Scenario 4) y `external_phone=NULL` (no-op) sin violar el CHECK ni requerir migración. El literal `external_name='ANONIMIZADO'` del spec es incompatible con el esquema y no aplica a filas registradas.
- Export JSON (Art. 20) fuera de alcance v1.0 (Fase 2 por el spec).
