## Why

La capability `exportaciones-rgpd` (derecho al olvido, RGPD Art. 17) está especificada pero **no implementada**: hoy `DELETE /api/admin/usuarios/{id}` solo hace un soft-delete (`status=INACTIVE` + audit `USER_DEACTIVATED`), sin **anonimizar** los datos personales, revocar sesiones ni invalidar OTPs. Es un requisito legal (AEPD) para v1.0.

## What Changes

- `DELETE /api/admin/usuarios/{id}` pasa a realizar la **anonimización irreversible** en una única transacción atómica (RN-RGPD-01/05/06/07):
  - Sobrescribe campos personales de `users`: `first_name='ANONIMIZADO'`, `last_name='ANONIMIZADO'`, `email='anonimized-{id}@padelpro.local'`, `phone=NULL`, `telegram_chat_id=NULL`, `telegram_linked_at=NULL`; `status=INACTIVE`.
  - Anonimiza `participants` del usuario: `external_name='ANONIMIZADO'`, `external_phone=NULL` donde `user_id={id}`.
  - Revoca todos los `refresh_tokens` del usuario (`revoked=true`) e invalida todos los `otp_codes` activos (`used=true`).
  - Registra `audit_log` con `action='USER_ANONYMIZED'` (ejecutor = admin, `entity_type='USER'`, `entity_id={id}`).
  - Responde **204 No Content**.
- **NO** modifica `reservations`, `payments` ni el histórico de `audit_log` (conservación fiscal 5 años, RN-RGPD-02; inmutabilidad de auditoría, RN-RGPD-05).
- Los logs del proceso no contienen datos personales (RN-RGPD-04).
- **BREAKING (comportamiento):** el "borrado" de usuario deja de ser una simple desactivación reversible y pasa a ser una **anonimización irreversible**. El código de auditoría cambia de `USER_DEACTIVATED` a `USER_ANONYMIZED`.

## Capabilities

### New Capabilities
<!-- Ninguna: se implementa la capability exportaciones-rgpd ya especificada. -->

### Modified Capabilities
<!-- Sin cambios de requisitos: el spec de exportaciones-rgpd ya define el comportamiento. Change de implementación. -->

## Impact

- **Backend:** amplía `UserAdminService.deactivateUser` (→ anonimización); necesita puertos para revocar `refresh_tokens` (módulo auth) e invalidar `otp_codes` (módulo otp) y actualizar `participants` (módulo reservas). Cambia `AuditActions.USER_DEACTIVATED` → `USER_ANONYMIZED` en este flujo.
- **Sin cambios de esquema** (usa columnas/FK existentes; las FK `audit_log.user_id` y `payments.registered_by_id` ya están definidas como corresponda).
- **Frontend:** el botón de "eliminar usuario" del panel admin ahora anonimiza irreversiblemente — conviene reforzar el copy de confirmación (follow-up menor si aplica; el endpoint es el mismo).
- **Fase del producto:** fase-1 (Wave 5). La **exportación JSON (Art. 20)** queda **fuera de alcance** (diferida a Fase 2 por el propio spec).

## Fuera de alcance

- `GET /api/usuarios/me/exportar` (portabilidad JSON, Art. 20): diferido a Fase 2 por el spec.
- Borrado físico de registros (prohibido por RN-RGPD-01/02/05: se anonimiza, no se borra).
