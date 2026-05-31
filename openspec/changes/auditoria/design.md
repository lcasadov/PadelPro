## Context

La tabla `audit_log` fue creada en V3 como parte de `auth-local` con las columnas mínimas para el bootstrap-mvp (`action`, `user_id`, `ip_address`, `details`, `created_at`). La entidad JPA `AuditLog`, el puerto `AuditLogRepositoryPort` y el `AuditLogRepository` ya existen. Lo que falta:

1. **Columnas de contexto**: `entity_type`, `entity_id` (para vincular la entrada al objeto de negocio afectado), `channel` (WEB / TELEGRAM / SYSTEM). Sin ellas las capabilities futuras (`reservas`, `pagos-redsys`) no pueden guardar entradas con contexto completo.
2. **Capacidad de consulta**: `AuditLogRepository` solo tiene los métodos CRUD heredados de `JpaRepository`. No hay query para filtrar por action, usuario o rango de fechas.
3. **Endpoint REST**: no existe `GET /api/admin/audit`. Los ADMINs no pueden consultar el log desde la aplicación.
4. **Longitud de `action`**: `VARCHAR(50)` es insuficiente para códigos como `PAYMENT_WEBHOOK_INVALID_SIGNATURE` (38 chars) y otros futuros. El spec define `VARCHAR(100)`.

---

## Goals / Non-Goals

**Goals:**
- Flyway V5: añadir `entity_type`, `entity_id`, `channel` a `audit_log`; ampliar `action` a `VARCHAR(100)`.
- Actualizar `AuditLog` entity y `AuditLogRepositoryPort` para soportar los nuevos campos y la búsqueda filtrada.
- Implementar `GET /api/admin/audit` con paginación y filtros opcionales (`action`, `userId`, `entityType`, `from`, `to`).
- TDD strict: unit → integration (Testcontainers) → E2E (TestRestTemplate), cobertura ≥ 80%.

**Non-Goals:**
- Escritura de entradas con los nuevos campos — las capabilities `reservas`, `pagos-redsys`, etc. añadirán esas llamadas cuando se implementen.
- Dashboard/UI del log de auditoría — `administracion-club` (Fase 2).
- Exportación CSV del log — `exportaciones-rgpd`.
- Particionamiento de la tabla por año — recomendado cuando supere 500k filas; fuera de alcance v1.0.

---

## Decisions

### D-1 — Migración V5: columnas nullable para retrocompatibilidad

```sql
ALTER TABLE audit_log
    ALTER COLUMN action TYPE VARCHAR(100),
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS entity_id   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS channel     VARCHAR(20) DEFAULT 'WEB';
```

Las tres nuevas columnas son nullable (retrocompatibilidad con las ~N entradas existentes en `audit_log`). El `DEFAULT 'WEB'` en `channel` asegura que las inserciones que no especifiquen canal hereden el valor correcto.

*Alternativa descartada*: crear una tabla `audit_log_v2` con JOIN. Añade complejidad innecesaria; una migración additive es más sencilla y más rápida.

### D-2 — Método de búsqueda filtrada en `AuditLogRepositoryPort`

```java
Page<AuditLog> findFiltered(AuditLogFilter filter, Pageable pageable);
```

`AuditLogFilter` es un record inmutable:
```java
public record AuditLogFilter(
    String action,
    Long userId,
    String entityType,
    OffsetDateTime from,
    OffsetDateTime to
) {}
```

La implementación usa `Specification<AuditLog>` (Spring Data JPA Specification) para construir la query dinámicamente — solo aplica los predicados de los campos no-null. Evita N variantes de método.

*Alternativa descartada*: `@Query` JPQL estático con todos los parámetros opcionales. Con 5 filtros genera un `WHERE (a IS NULL OR col=a) AND (b IS NULL OR col2=b)...` ineficiente.

### D-3 — `GET /api/admin/audit` con paginación por defecto

```
GET /api/admin/audit
  ?action=ACCESS_DENIED
  &userId=42
  &entityType=RESERVATION
  &from=2026-01-01T00:00:00Z
  &to=2026-12-31T23:59:59Z
  &page=0        (default 0)
  &size=20       (default 20, max 100)
  &sort=createdAt,desc  (default)
```

Respuesta `PagedAuditLogResponse`:
```json
{
  "content": [
    {
      "id": 1,
      "action": "ACCESS_DENIED",
      "userId": 42,
      "userEmail": "user@example.com",
      "ipAddress": "1.2.3.4",
      "details": "uri=/api/admin/usuarios",
      "entityType": null,
      "entityId": null,
      "channel": "WEB",
      "createdAt": "2026-05-31T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

El campo `size` se limita a 100 para evitar payloads gigantes. Si `size > 100`, devuelve 400 con código `VALIDATION_ERROR`.

### D-4 — `AuditLogService` en la capa Application

Nueva clase `AuditLogService` en `com.padelpro.auth.application.service`:
- Inyecta `AuditLogRepositoryPort` (no `AuditLogRepository` directamente — ArchUnit Rule 3).
- Método: `Page<AuditLogEntryResponse> findAuditLogs(AuditLogFilter filter, Pageable pageable)`.
- Mapeo `AuditLog` → `AuditLogEntryResponse` interno (sin MapStruct — no hay dependencia de esa librería en el proyecto).

### D-5 — `AdminAuditController` en infrastructure.web

```java
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {
    // GET / → llama AuditLogService, retorna PagedAuditLogResponse
    // RBAC: heredado de SecurityConfig (hasRole("ADMIN") para /api/admin/**)
    // No anotación @PreAuthorize necesaria — el filtro de Spring Security lo cubre
}
```

El `userId` del request parameter es `Long` (no `String`) para evitar inyecciones de tipo.

### D-6 — Estrategia TDD

Ciclo estricto:
1. **Unit tests (RED)** → `AuditLogServiceTest` con Mockito (sin Spring): verificar paginación, filtros vacíos, filtro por action, filtro por userId, límite de size.
2. **Integration tests (RED)** → `AdminAuditIntegrationTest` con Testcontainers + MockMvc: verificar endpoint, paginación, filtros, RBAC (USER recibe 403, no autenticado 401).
3. **E2E tests (RED)** → `AdminAuditE2ETest` con TestRestTemplate: verificar que una entrada escrita por `AuthService` aparece en el log filtrado.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| `Specification<AuditLog>` requiere `JpaSpecificationExecutor` en `AuditLogRepository` | Añadir `extends JpaSpecificationExecutor<AuditLog>` a la interfaz |
| `ALTER COLUMN action TYPE VARCHAR(100)` puede fallar si hay un índice funcional sobre `action` | V3 solo crea un índice estándar `idx_audit_log_action` — no funcional; la migración es segura |
| Paginación sin ordenación explícita puede devolver orden no determinista | Default `sort=createdAt,desc` en el controller; índice `idx_audit_log_created_at` ya existe |
| ArchUnit Rule 3: `AuditLogService` no debe inyectar `AuditLogRepository` directamente | Inyectar solo `AuditLogRepositoryPort`; la nueva interfaz `findFiltered` se añade al puerto |

---

## Migration Plan

1. `V5__evolve_audit_log.sql` — `ALTER TABLE audit_log` (additive, zero-downtime).
2. Actualizar `AuditLog` entity — no hay datos que migrar, las columnas nuevas serán null en filas antiguas.
3. Actualizar `AuditLogRepository` para extender `JpaSpecificationExecutor<AuditLog>`.
4. Implementar `AuditLogFilter`, `AuditLogEntryResponse`, `PagedAuditLogResponse`.
5. Implementar `AuditLogService`.
6. Implementar `AdminAuditController`.
7. Ejecutar tests y verificar cobertura ≥ 80%.

**Rollback**: Flyway no soporta rollback automático. Si hay que revertir V5, ejecutar el script inverso manualmente:
```sql
ALTER TABLE audit_log
    DROP COLUMN IF EXISTS channel,
    DROP COLUMN IF EXISTS entity_id,
    DROP COLUMN IF EXISTS entity_type,
    ALTER COLUMN action TYPE VARCHAR(50);
```
(Solo seguro si no hay filas con `entity_type`/`entity_id` no-null, es decir, antes de desplegar las capabilities que los escriben.)

---

## Open Questions

*(Ninguna — decisiones cerradas para este change)*
