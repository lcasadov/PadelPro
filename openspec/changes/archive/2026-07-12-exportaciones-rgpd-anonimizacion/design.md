## Context

`DELETE /api/admin/usuarios/{id}` → `UserAdminService.deactivateUser(targetId, adminId)` hoy solo hace `status=INACTIVE` + `audit_log USER_DEACTIVATED`, con guard de auto-desactivación (RN-AUTH-05). La capability `exportaciones-rgpd` exige que ese endpoint anonimice de forma irreversible. Las entidades y sus reglas de conservación están definidas en el spec: `users`/`participants` se anonimizan; `reservations`/`payments`/`audit_log` histórico se conservan.

## Goals / Non-Goals

**Goals:**
- Anonimización atómica e irreversible al invocar el DELETE (RN-RGPD-01/05/06/07).
- Preservar datos fiscales/contables y de auditoría (RN-RGPD-02/05).
- Sin fuga de datos personales en logs ni en errores (RN-RGPD-03/04).

**Non-Goals:**
- Exportación JSON (Art. 20) — Fase 2.
- Borrado físico de filas.

## Decisions

### D1 — Extender el flujo del DELETE existente, no crear endpoint nuevo (RN-RGPD-01)
El spec mapea el derecho al olvido al `DELETE /api/admin/usuarios/{id}` ya existente. Se reescribe `deactivateUser` como **anonimización** (mantiene el guard de auto-borrado y el `status=INACTIVE`, y añade el resto). Se mantiene el mismo endpoint/respuesta 204 para no romper el contrato de la API.

### D2 — Orquestación cross-módulo vía puertos de salida (hexagonal)
La anonimización toca varios agregados: `users` (usuarios/auth), `refresh_tokens` (auth), `otp_codes` (otp), `participants` (reservas). `UserAdminService` orquesta llamando a **puertos de salida** de cada módulo (uno para revocar refresh tokens por user, otro para invalidar OTPs por user, otro para anonimizar participants por user). Si algún puerto no existe aún, se añade el método mínimo de escritura. Todo dentro de **una `@Transactional`** para atomicidad (RN-RGPD-01).

### D3 — `USER_ANONYMIZED` como acción de auditoría, ejecutor = admin
Se sustituye `USER_DEACTIVATED` por `USER_ANONYMIZED` en este flujo (el spec lo exige). El `audit_log` registra al **admin ejecutor** como `user_id`, `entity_type='USER'`, `entity_id={id target}`. El histórico de `audit_log` del usuario anonimizado NO se toca (su FK `user_id` puede quedar o pasar a NULL según el esquema; no se borra — RN-RGPD-05).

### D4 — Idempotencia / doble anonimización
Anonimizar un usuario ya anonimizado (`email` ya `anonimized-{id}@…`, `status=INACTIVE`) debe ser seguro: reejecuta sobrescribiendo los mismos valores neutros, revoca/invalida lo que quede, y registra otra entrada de auditoría (o se puede cortocircuitar si ya está anonimizado). Se opta por reejecución idempotente para simplicidad, sin fallar.

### D5 — Sin datos personales en logs (RN-RGPD-04)
El servicio no loggea el email/teléfono/nombre originales. Solo ids técnicos. Los mensajes de error del endpoint no exponen datos de otros usuarios (RN-RGPD-03).

## Risks / Trade-offs

- **[Irreversibilidad sorprende al admin]** → Mitigación: el spec lo define así; documentar el cambio de semántica (deactivate→anonymize) en el PR y reforzar el copy de confirmación en el front (follow-up menor). El endpoint no cambia de forma.
- **[Ruptura de tests existentes de `deactivateUser`]** → Mitigación: actualizar los tests al nuevo comportamiento (anonimización + `USER_ANONYMIZED`).
- **[Atomicidad entre módulos]** → Mitigación: única `@Transactional` en el servicio orquestador; todos los puertos participan en la misma transacción (mismo `EntityManager`/datasource).
- **[FK que impidan la operación]** → Mitigación: no se borran filas (solo UPDATEs), así que las FK RESTRICT de reservas/pagos no bloquean; se respeta el esquema.

## Migration Plan

1. Añadir/confirmar puertos de escritura: revocar refresh_tokens por user, invalidar otp_codes por user, anonimizar participants por user.
2. Reescribir `deactivateUser` como anonimización atómica + `USER_ANONYMIZED`.
3. Actualizar tests. Sin migración de esquema.
4. Rollback: revertir el código; el endpoint vuelve al soft-delete. Los usuarios ya anonimizados permanecen anonimizados (irreversible por diseño).
