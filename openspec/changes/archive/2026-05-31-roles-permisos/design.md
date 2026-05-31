## Context

Spring Security está configurado con `hasRole('ADMIN')` para `/api/admin/**` y `authenticated()` para el resto. El `JwtAuthFilter` valida firma y expiración del token. Sin embargo:

1. **Hueco de estado**: el status del usuario (`PENDING`, `INACTIVE`) no se re-verifica en cada request. Un token emitido a un ACTIVE user sigue siendo válido aunque ese usuario sea desactivado antes de que expire.
2. **403 silenciosos**: los accesos denegados no se auditan. No hay forma de saber post-hoc qué intentos no autorizados se produjeron.
3. **Respuestas inconsistentes**: Spring Security devuelve HTML o respuestas vacías para 401/403. Los clientes esperan `ErrorResponse` JSON.
4. **Webhook routes sin configurar**: `/api/bot/telegram` y `/api/pagos/webhook` aún no existen; si se crean sin configuración explícita, Spring Security las protegerá con JWT lo cual es incorrecto (usan validación propia).
5. **Propiedad de recurso sin contrato**: las reglas RN-AUTH-01..04 (quien puede ver/cancelar/pagar su reserva) no tienen una abstracción formal. Sin un puerto definido, cada servicio las implementará de forma ad-hoc.
6. **Sin umbral de cobertura**: no hay configuración que garantice el 80% de cobertura mínima.

---

## Goals / Non-Goals

**Goals:**
- Verificar `user.status == ACTIVE` en cada request autenticado (no solo en login).
- Respuestas JSON uniformes para 401 y 403.
- Audit log de todo 403.
- Rutas webhook configuradas como `permitAll()` con validación delegada.
- Contrato de propiedad de recurso (`ResourceOwnershipPort`) formalmente definido.
- JaCoCo con umbral 80%.
- ArchUnit: propiedad de recurso en capa `application`, nunca en `infrastructure`.

**Non-Goals:**
- Implementar las verificaciones de propiedad (RN-AUTH-01..04) — eso es `reservas`.
- UI de gestión de roles — eso es `administracion-club` (Fase 2).
- Revocación activa de JWT — sin lista negra en v1.0 (coste/beneficio fuera de alcance).

---

## Decisions

### D-1 — `UserStatusFilter`: re-verificación de status por request

Nuevo `OncePerRequestFilter` que se ejecuta DESPUÉS de `JwtAuthFilter` en la cadena:

```
JwtAuthFilter → UserStatusFilter → Spring Security authorization
```

Si el `SecurityContext` tiene una autenticación válida (JWT OK), el filtro carga el usuario por `userId` del principal y verifica `user.status == ACTIVE`. Si no está ACTIVE, llama a `SecurityContextHolder.clearContext()` y escribe directamente la respuesta `403` con código `ACCOUNT_NOT_ACTIVE`.

**Razón de orden**: después de `JwtAuthFilter` porque solo actúa si hay JWT válido. Antes de la capa de autorización de Spring Security porque necesita bloquear independientemente del endpoint.

**Impacto en rendimiento**: una query a BD por cada request autenticado. Acceptable en v1.0 con caché L1 de Hibernate (la entidad user probablemente ya esté en sesión). Si se necesita optimización futura: añadir caché Redis con TTL = duración del access token (15 min).

*Alternativa descartada*: añadir `status` como claim en el JWT y verificarlo sin BD. Descartada porque el JWT es inmutable — si el status cambia, el claim queda obsoleto durante toda la vida del token.

### D-2 — `CustomAccessDeniedHandler` + `CustomAuthenticationEntryPoint`

Dos beans nuevos en `SecurityConfig`:

- `CustomAuthenticationEntryPoint`: maneja `401 Unauthorized`. Escribe `ErrorResponse { code: "AUTH_REQUIRED", message: "Authentication required" }`. Registra en audit_log con acción `ACCESS_UNAUTHENTICATED` y `user_id=null`.
- `CustomAccessDeniedHandler`: maneja `403 Forbidden`. Escribe `ErrorResponse { code: "ACCESS_DENIED", message: "Insufficient permissions" }`. Registra en audit_log con acción `ACCESS_DENIED`, `user_id` del token (si existe), `ip_address` y `details=requestUri`.

*Razón*: respuestas JSON uniformes para que el frontend pueda parsear errores de forma predecible sin condicionales por tipo de contenido.

### D-3 — Rutas webhook como `permitAll()` en SecurityFilterChain

```java
.requestMatchers("/api/bot/telegram", "/api/pagos/webhook").permitAll()
```

La validación de seguridad de estos endpoints la realiza su adaptador respectivo (secret token de Telegram en header `X-Telegram-Bot-Api-Secret-Token`, HMAC SHA-256 de Redsys). Configurarlos como `permitAll()` en Spring Security NO los deja sin protección — simplemente delega la auth a la capa de aplicación en lugar de a Spring Security.

### D-4 — `ResourceOwnershipPort` en `domain.port.in`

```java
public interface ResourceOwnershipPort {
    boolean isOwner(Long userId, String resourceType, String resourceId);
    boolean isParticipant(Long userId, String reservationId);
}
```

Interfaz vacía en `com.padelpro.auth.domain.port.in` (o un paquete compartido como `com.padelpro.shared.domain`). La implementación concreta vendrá en `reservas`. Este change solo declara el contrato para que `HexagonalArchitectureTest` pueda validar que los servicios lo usan en lugar de llamar directamente a repositorios de reservas.

*Alternativa descartada*: no declarar el puerto y dejar que `reservas` lo defina. Descartada porque sin el contrato previo, no hay regla ArchUnit que proteja la arquitectura.

### D-5 — JaCoCo: umbral 80% configurado en `pom.xml`

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <configuration>
    <excludes>
      <exclude>**/dto/**</exclude>
      <exclude>**/exception/**</exclude>
      <exclude>**/*Application.class</exclude>
    </excludes>
  </configuration>
  <execution>
    <id>jacoco-check</id>
    <goals><goal>check</goal></goals>
    <configuration>
      <rules>
        <rule>
          <element>BUNDLE</element>
          <limits>
            <limit>
              <counter>LINE</counter>
              <value>COVEREDRATIO</value>
              <minimum>0.80</minimum>
            </limit>
          </limits>
        </rule>
      </rules>
    </configuration>
  </execution>
</plugin>
```

*Razón*: el mandato es 80% de cobertura de líneas. Se excluyen DTOs (solo campos), excepciones (solo constructores), y la clase Application (solo `main`). Los filtros, servicios, handlers y configuración deben alcanzar el 80%.

### D-6 — ArchUnit: nueva regla de propiedad de recurso

Nueva regla en `HexagonalArchitectureTest`:

```java
// R-ownership: resource ownership checks must NOT be in infrastructure.web
noClasses()
    .that().resideInAPackage("..infrastructure.web..")
    .should().callMethodWhere(target().getOwner()
        .isAssignableTo(ResourceOwnershipPort.class))
    .because("Resource ownership checks belong in application services, never in controllers.");
```

### D-7 — Estrategia TDD para este change

Ciclo estricto por cada componente:

1. **Unit tests (RED)** → implementación (GREEN) → refactor:
   - `UserStatusFilterTest`: token válido + usuario INACTIVE → 403; token válido + usuario ACTIVE → continúa; no hay token → sin efecto.
   - `CustomAccessDeniedHandlerTest`: verifica JSON response + llamada a audit log.
   - `CustomAuthenticationEntryPointTest`: verifica JSON response 401.

2. **Integration tests (Testcontainers)** → cubren escenarios reales end-to-end:
   - `SecurityIntegrationTest`: todos los scenarios de R-1, R-2, R-3 (los de R-3 con stubs de reservas).

3. **E2E tests** (en este proyecto: Testcontainers + HTTP real sin MockMvc):
   - `SecurityE2ETest`: tests con `TestRestTemplate` contra puerto real, verificando headers, body, cookies y respuestas completas sin abstracciones de MockMvc.

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| `UserStatusFilter` añade latencia por query a BD | En v1.0 aceptable. Solución futura: caché con TTL 15 min (igual al access token). |
| JaCoCo puede fallar el build si el 80% no se alcanza tras añadir código legacy | Revisar exclusiones. Ajustar umbral a 75% si el módulo auth ya tiene código difícil de testear (reflexión, interfaces). |
| `ResourceOwnershipPort` vacío puede confundir a futuros implementadores | Documentar con Javadoc que la implementación concreta viene en `reservas`. |

---

## Migration Plan

1. Añadir `UserStatusFilter` y beans de security a la config existente (compatible con tests actuales).
2. Añadir JaCoCo al `pom.xml` — el build fallará hasta que los tests unitarios nuevos alcancen el umbral.
3. Declarar `ResourceOwnershipPort` y nueva regla ArchUnit (la regla pasa en verde desde el primer día porque nadie la viola aún).
4. Todos los tests existentes (auth-local, usuarios) deben seguir en verde.

---

## Open Questions

*(Ninguna — todas las decisiones están cerradas para este change)*
