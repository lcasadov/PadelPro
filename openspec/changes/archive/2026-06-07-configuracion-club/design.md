## Context

La tabla `system_config` es nueva y actúa como singleton (id=1 siempre). El sistema necesita un lugar centralizado para guardar configuración que hoy está fragmentada (env vars, constantes en código, etc.). La encriptación de secretos es crítica porque habrá credenciales Redsys, token de Telegram, etc. que no pueden estar en claro en la BD.

El ADMIN debe poder actualizar la configuración sin tocar el código. Pero la validación es crítica: cambios inválidos (ej. payment_gateway a REDSYS sin credenciales) pueden romper el sistema.

---

## Goals / Non-Goals

**Goals:**
- Crear tabla singleton `system_config` con campos de configuración
- Implementar encriptación AES-256-GCM para secretos (telegram_bot_token, redsys_merchant_key, etc.)
- Endpoints REST `GET /api/admin/sistema/config` y `PATCH /api/admin/sistema/config` con validación
- Solo ADMIN puede leer/modificar (vía SecurityFilterChain `/api/admin/**`)
- Entidad JPA + DTOs de request/response
- TDD completo (unit → integration → E2E)

**Non-Goals:**
- UI de administración (viene en `administracion-club` Fase 2)
- Historial de cambios (auditoría de configuración) — puede hacerse en futuro
- Validación contra servicios reales (ej. verificar credenciales Redsys con servidor)
- Cache en memoria de configuración (Fase 2, si performance lo requiere)

---

## Decisions

### D-CONF-01 — ENCRYPTION_KEY: env var sin fallback

**Decisión:** ENCRYPTION_KEY es una variable de entorno obligatoria sin fallback (igual que JWT_SECRET).

```bash
export ENCRYPTION_KEY="tu-clave-de-32-caracteres-minimo"
```

**Rationale:** 
- RN-SEC-02 (si existe) requiere que secretos estén cifrados en reposo
- No hay forma segura de derivar la clave desde JWT_SECRET (mezcla de propósitos)
- KMS es overkill para v1.0
- Operaciones en producción saben manejar env vars sensibles

**Alternativa descartada:** Derivar de JWT_SECRET — violería separación de propósitos, haría que comprometer JWT_SECRET = comprometer todos los secretos de config.

---

### D-CONF-02 — AES-256-GCM para encriptación simétrica

**Decisión:** Los campos sensibles se cifran con AES-256-GCM + IV aleatorio + autenticación.

**Rationale:**
- GCM = autenticación incluida (detecta tamperizaciones)
- IV aleatorio por cada cifrado evita patrones en la BD
- Spring tiene support nativo via `javax.crypto.Cipher`
- Estándar de facto en la industria

**Campos a cifrar:**
- `telegram_bot_token`
- `redsys_merchant_id`
- `redsys_merchant_key`

**Campos sin cifrar (públicos):**
- `club_name`, `club_description` (público)
- `pista_state` (público)
- `payment_gateway` (público)
- `max_participants_per_pista` (público)

---

### D-CONF-03 — Singleton via constraint + validación en aplicación

**Decisión:** La tabla solo tiene 1 fila (id=1 siempre). Se fuerza con:
1. PRIMARY KEY (id)
2. CHECK constraint: `id = 1`
3. Validación en el controller

```sql
ALTER TABLE system_config ADD CONSTRAINT singleton_config CHECK (id = 1);
```

**Rationale:**
- Evita N filas de config confusas
- Una fila = verdad única
- Si alguien intenta INSERT directo, falla en BD

**Alternativa descartada:** Usar un campo UUID como PK — más complejo y sin ventaja para singleton.

---

### D-CONF-04 — SystemConfigDTO para respuesta sin secretos

**Decisión:** El endpoint `GET /api/admin/sistema/config` devuelve un DTO que oculta secretos (null o "***REDACTED***").

```java
public record SystemConfigResponse(
    String clubName,
    String clubDescription,
    PistaState pistaState,
    PaymentGateway paymentGateway,
    Integer maxParticipantsPerPista,
    Boolean telegramBotConfigured,  // true/false, nunca el token
    Boolean redsysConfigured,        // true/false, nunca las creds
    OffsetDateTime updatedAt
) {}
```

**Rationale:** RN-SEC-02 (no exponer secretos). El ADMIN necesita saber si está configurado, no ver el valor en claro en la response HTTP.

---

### D-CONF-05 — Validación en PATCH

**Decisión:** Al actualizar `system_config`, se valida:
1. Si `payment_gateway = REDSYS`, requiere `redsys_merchant_id` + `redsys_merchant_key` (no null)
2. Si `payment_gateway = CASH`, los campos Redsys pueden ser null
3. Si `telegram_bot_token` está set, debe ser no-empty y >20 chars (válido aproximado)
4. `max_participants_per_pista` debe ser > 0

**Rationale:**
- Evita estados inválidos (ej. REDSYS sin credenciales)
- Fail-fast en el controller antes de guardar
- Mejora la experiencia del ADMIN (feedback inmediato)

---

### D-CONF-06 — Injection pattern para acceso desde otros servicios

**Decisión:** `SystemConfigService` se inyecta directamente en las capabilities que la necesitan.

```java
// En PaymentService, AuthOtpService, etc.
public class PaymentService {
    private final SystemConfigService configService;
    
    public PaymentService(SystemConfigService configService) {
        this.configService = configService;
    }
    
    public void processPayment() {
        String merchantKey = configService.getRedsysMerchantKey();
        // ... usar merchantKey
    }
}
```

No hay eventos, no hay REST interno, no hay cache distribuido.

**Rationale:**
- Simplicidad para v1.0 (single instance)
- Testeable (mock SystemConfigService en tests)
- Sin overhead de coordinación entre instancias
- El coupling es aceptable ahora (especializado para cada capability)

---

### D-CONF-07 — Nomenclatura: SystemConfigService + SystemSecretsService

**Decisión:** Split conceptual en DOS servicios con UNA tabla:

```java
// Pública: configuración del club (club_name, pista_state, max_participants)
public class SystemConfigService {
    public SystemConfigResponse getConfig() { ... }
    public void updateConfig(UpdateConfigRequest req) { ... }
}

// Privada: secrets descifrados (telegram_bot_token, redsys_keys)
public class SystemSecretsService {
    public String getTelegramBotToken() { ... }
    public String getRedsysMerchantKey() { ... }
    // Internamente: inyecta EncryptionService para descifrar
}
```

**En BD:** Una sola tabla `system_config`, todos los campos cifrados o sin cifrar como hoy.

**Rationale:**
- Semánticamente claro: "secretos" ≠ "configuración"
- Auditoría limpia (CONFIG_UPDATED vs SECRETS_ROTATED en audit_log)
- Prepara Fase 2: es fácil mover `SystemSecretsService` a un vault externo sin tocar `SystemConfigService`
- Reduce acceso accidental a secretos (solo quien inyecta `SystemSecretsService` los obtiene)

---

### D-CONF-08 — Desencriptación siempre en servicio, nunca en controller

**Decisión:** Un único punto de desencriptación: `SystemSecretsService`.

```
         Controller (público)
              ↓
        SystemConfigService (GET config sin secrets)
              ↓
        SystemSecretsService (privado, descifra aquí)
              ↓
        EncryptionService (AES-256-GCM)
              ↓
        Entity (system_config, cifrado en BD)
```

- **Response al ADMIN (GET /api/admin/sistema/config):** valores descifrados visible (porque ADMIN necesita ver qué está configurado)
- **Otros servicios (inyectan SystemSecretsService):** reciben plaintext descifrado en memoria
- **En BD:** siempre cifrado (ningún secret en plaintext)

**Rationale:**
- Un único punto de control para desencriptación
- Imposible que un secret escape a logs/responses sin descifrar
- Fácil auditar: grep "getRedsysMerchantKey()" encontra todos los llamadores
- EncryptionService es un utility reutilizable para future secrets

---

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| ENCRYPTION_KEY leaks en logs/monitoring | No loguear el valor, usar mascarado en debug. Env vars en secrets manager en prod. |
| Si ENCRYPTION_KEY cambia, los datos cifrados ya guardados son ilegibles | Operaciones saben esto. En Fase 2 se puede implementar rotación de claves con versionado. |
| `system_config` se convierte en "dumping ground" de config | Documentar claramente qué va aquí vs en variables de entorno. Code review. |
| Desencriptación en cada request (performance) | Aceptable para v1.0 — <1MB de datos. Cache en Fase 2 si necesario. |
| ArchUnit Rule 3: Service debe inyectar EncryptionService (infraestructura) | EncryptionService es un utility de dominio, no infraestructura. Se inyecta en Service, es OK. |

---

## Migration Plan

1. **Flyway V6:** Crear tabla `system_config` con constraint singleton
2. **Insert inicial:** Fila (id=1) con valores default:
   - `club_name = "Mi Club de Pádel"` (placeholder)
   - `payment_gateway = CASH` (default seguro, sin credenciales)
   - Secretos = null
3. **EncryptionService:** Implementar AES-256-GCM
4. **Entidad + DTOs:** Mapear tabla a JPA
5. **Controller + Service:** Implementar GET/PATCH con validación
6. **Tests:** Unit → Integration → E2E
7. **Verificación:** Verificar que no hay secret leaks en logs/responses

**Rollback:** Eliminar tabla (no hay dependencias de datos en v1.0).

---

## Open Questions

_(Ninguna — todas resueltas por D-CONF-01 a D-CONF-05)_
