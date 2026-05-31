# Proposal: Auditoria — API de lectura + columnas faltantes

**Change slug:** `auditoria`
**Estado:** proposed
**Issue GitHub:** TBD
**Épica:** EP-01 Acceso e Identidad
**Milestone:** EP-01 Acceso e Identidad

---

## Why

La tabla `audit_log` (V3) existe y los servicios ya escriben entradas (`ACCESS_DENIED`, `USER_LOGIN_SUCCESS`, etc.), pero la tabla está incompleta respecto al spec: faltan las columnas `entity_type`, `entity_id` y `channel` que otras capabilities necesitarán. Además no existe ningún endpoint REST que permita al ADMIN consultar el log de auditoría — sin esa API los administradores no pueden hacer trazabilidad de seguridad, detectar anomalías ni acreditar cumplimiento RGPD Art. 5.

---

## What Changes

- **Flyway V5 — evolución de `audit_log`**: añadir columnas `entity_type VARCHAR(50)`, `entity_id VARCHAR(100)`, `channel VARCHAR(20) DEFAULT 'WEB'` (todas nullable para compatibilidad con filas existentes). Ampliar `action` de `VARCHAR(50)` a `VARCHAR(100)` (widening seguro).
- **`AuditLog` entity**: añadir los tres campos nuevos al modelo JPA.
- **`AuditLogRepositoryPort`**: añadir método `findFiltered(AuditLogFilter, Pageable)` para soportar la consulta filtrada con paginación.
- **`AuditLogService`** (application layer): encapsula la lógica de búsqueda y mapeo al DTO de respuesta.
- **`AdminAuditController`** — nuevo endpoint `GET /api/admin/audit` con filtros opcionales por `action`, `userId`, `entityType`, `from`, `to`, y paginación. Solo accesible para ADMIN.
- **`AuditLogEntryResponse` DTO**: `id`, `action`, `userId`, `userEmail`, `ipAddress`, `details`, `entityType`, `entityId`, `channel`, `createdAt`.
- **`PagedAuditLogResponse` DTO**: wrapper paginado estándar (`content`, `page`, `size`, `totalElements`, `totalPages`).

---

## Capabilities

### New Capabilities

_(ninguna)_

### Modified Capabilities

- `auditoria`: promueve el endpoint `GET /api/admin/audit` de "Fase 2 pendiente" a **Fase 1 implementada**. Añade los scenarios del endpoint REST que el spec original marcaba como futuros.

---

## Impact

**Backend:**
- Nueva migración Flyway `V5__evolve_audit_log.sql`.
- `AuditLog.java` actualizado con tres campos nuevos.
- `AuditLogRepositoryPort` ampliado con método de búsqueda filtrada.
- Nuevo `AuditLogService` en `com.padelpro.auth.application.service`.
- Nuevo `AdminAuditController` en `com.padelpro.auth.infrastructure.web`.
- Nuevos DTOs: `AuditLogEntryResponse`, `PagedAuditLogResponse`, `AuditLogFilter`.

**Frontend:**
- Sin cambios de implementación en este change. El endpoint queda disponible para que `administracion-club` (Fase 2) lo consuma.

**Base de datos:**
- Migración de columnas `ALTER TABLE audit_log` — retrocompatible (nullable / `DEFAULT 'WEB'`).

**Seguridad:**
- El endpoint hereda `hasRole('ADMIN')` del `SecurityFilterChain` existente (`/api/admin/**`).

**Dependencias:**
- Requiere `auth-local` (tabla `audit_log` creada en V3) y `roles-permisos` (SecurityConfig con ADMIN guard).
- Desbloquea: `administracion-club` (Fase 2) y la validación RGPD de `exportaciones-rgpd`.

---

## Alcance fuera del change

| Capacidad excluida | Change futuro |
|---|---|
| Escritura de entradas con `entity_type`/`entity_id`/`channel` | `reservas`, `pagos-redsys`, `auth-otp-telegram` (cada capability añade sus propias llamadas a `AuditLogRepositoryPort`) |
| Dashboard visual del log | `administracion-club` (Fase 2) |
| Exportación CSV del log | `exportaciones-rgpd` |
| Purga/archivado automático de filas antiguas | Tarea ops — fuera del scope de PadelPro v1.0 |
| Job nocturno de mantenimiento de `audit_log` | Fase 2 |
