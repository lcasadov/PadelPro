# PadelPro — Diseño de Seguridad

> **Versión:** 1.0 · **Fecha:** 2026-05-22
>
> Este documento es la referencia normativa de seguridad para `backend-architect` (implementación), `api-tester` (pruebas OWASP), `verification-specialist` y `reality-checker` (validación).
> Cada control incluye el apartado **"Cómo se prueba"** para que `api-tester` pueda derivar casos directamente.

---

## 1. Modelo de amenazas (STRIDE)

PadelPro es una aplicación de gestión de reservas de pádel para instalaciones pequeñas. El perfil de riesgo es **medio**: no almacena tarjetas de crédito, pero sí datos personales (RGPD), gestiona transacciones económicas reales (Redsys) y tiene un canal de mensajería público (Telegram) que puede recibir mensajes de actores no autenticados.

### 1.1 Activos críticos e inventario de amenazas

| Activo | Amenaza STRIDE | Vector concreto | Mitigación principal |
|---|---|---|---|
| **Credenciales de usuario** (`users.password_hash`) | Spoofing | Fuerza bruta o credential stuffing contra `POST /auth/login` | Rate limiting 5 intentos/min/IP + bloqueo temporal tras 10 fallos |
| **Credenciales de usuario** | Information Disclosure | Timing attack al comparar passwords | BCrypt `checkpw()` — tiempo constante nativo |
| **Credenciales de usuario** | Tampering | Inyección SQL en el login | Prepared statements (Spring Data JPA) |
| **JWT access token** | Spoofing | Token robado por XSS | Access token en memoria, no en localStorage |
| **JWT refresh token** | Spoofing | Cookie robada por red no cifrada | `httpOnly + Secure + SameSite=Strict`; solo HTTPS |
| **Reservas** | Repudiation | Usuario niega haber creado/cancelado la reserva | `audit_log` inmutable con acción, usuario, IP y timestamp |
| **Reservas** | Tampering | Usuario modifica la reserva de otro | Validación de `owner_id` en `ReservaApplicationService` + `@PreAuthorize` |
| **Reservas** | Elevation of Privilege | Usuario regular accede a endpoint admin (`/api/admin/*`) | RBAC en Spring Security; `@PreAuthorize("hasRole('ADMIN')")` |
| **Pagos Redsys** | Tampering | Manipulación del importe en el formulario de pago | El importe se calcula **solo en backend**; firma HMAC SHA-256 previene alteración |
| **Pagos Redsys** | Repudiation | Webhook Redsys recibido dos veces (replay) | Idempotencia: `payments.redsys_order_id UNIQUE`; si ya existe y `status=PAID` → ignorar |
| **Pagos Redsys** | Spoofing | Webhook falso que simula pago aprobado | Validación HMAC del webhook antes de procesar |
| **Bot Telegram** | Spoofing | Mensaje falso al webhook del bot (no proviene de Telegram) | `X-Telegram-Bot-Api-Secret-Token` header obligatorio |
| **Bot Telegram** | Spoofing | Suplantación de usuario en comandos del grupo | Vinculación previa `telegram_chat_id` ↔ cuenta PadelPro; comandos verificados por chat_id |
| **OTP** | Spoofing | Fuerza bruta de 6 dígitos (10^6 combinaciones) | TTL 10 min + máximo 3 intentos por OTP; invalidación automática tras expiración |
| **Datos personales** (`first_name`, `last_name`, `phone`, `email`) | Information Disclosure | Usuario accede a datos de otro usuario | Ownership check en todos los endpoints de lectura; RBAC estricto |
| **System config** (`redsys_secret_key`, `telegram_bot_token`, `smtp_password`) | Information Disclosure | Endpoint admin expone secrets en claro | Campos cifrados en BD (AES-256); DTO no serializa secrets completos |
| **Servidor On-Premise** | Denial of Service | Flood de requests a la API | Rate limiting en NGINX (límite de conexiones + requests por IP) |
| **PostgreSQL** | Information Disclosure | Acceso directo a BD si se expone el puerto | Escucha solo en red Docker interna; nunca puerto 5432 expuesto al exterior |

### 1.2 Riesgos residuales aceptados

| Riesgo | Razón de aceptación |
|---|---|
| Disponibilidad del bot Telegram si cae la plataforma Telegram | Riesgo externo fuera del control del sistema; canal secundario (email) como fallback |
| Disponibilidad del servidor único On-Premise | Contexto declarado: sin HA en v1.0; backups diarios como mitigación |
| Phishing de credenciales fuera del sistema | Fuera del alcance técnico; mitigado con 2FA OTP |

---

## 2. Autenticación

### 2.1 Mecanismo: JWT con doble token

Se usa el modelo de **access token + refresh token**:

- **Access token**: JWT de corta duración para autenticar cada request.
- **Refresh token**: token opaco de larga duración, almacenado en BD, para obtener nuevos access tokens sin requerir credenciales.

**Justificación del doble token vs sesión en servidor:** El sistema es stateless por diseño (arquitectura hexagonal, On-Premise). Un modelo de sesión en servidor requeriría Redis o sticky sessions, añadiendo complejidad operacional innecesaria para una instalación única.

### 2.2 Algoritmo de firma JWT: HS256

**Decisión:** `HS256` (HMAC con SHA-256) con secret de mínimo 256 bits.

**Justificación:** PadelPro tiene **una sola instancia de backend** como único verificador de tokens. `RS256` está diseñado para arquitecturas distribuidas donde múltiples servicios independientes verifican tokens usando la clave pública sin compartir secretos. En un sistema monolítico On-Premise de instancia única, `RS256` añade complejidad de gestión de certificados (rotación, trust store) sin beneficio de seguridad adicional. `HS256` con un secret de 256+ bits almacenado como variable de entorno es criptográficamente sólido para este contexto.

**Generación del secret:**
```bash
# En el servidor, al inicializar:
openssl rand -hex 32  # Genera 256 bits en hexadecimal
# Almacenar en APP_JWT_SECRET (variable de entorno, nunca en código ni en .env commiteado)
```

### 2.3 Lifetimes de tokens

| Token | Lifetime | Justificación |
|---|---|---|
| **Access token** | **15 minutos** | Ventana de exposición mínima si el token es robado. El frontend renueva automáticamente con el refresh token antes del vencimiento. |
| **Refresh token** | **7 días** | Equilibrio entre usabilidad (no forzar re-login frecuente en una app de club deportivo) y seguridad. Revocable individualmente. |

> **Nota:** El README (sección 3.7) indica 8h para el access token. Se corrige a 15 minutos por seguridad. La experiencia de usuario se mantiene con la renovación automática por el refresh token.

### 2.4 Payload del access token

```json
{
  "sub": "42",
  "login": "jdoe",
  "role": "USER",
  "iat": 1700000000,
  "exp": 1700000900,
  "jti": "uuid-único-por-token"
}
```

El `jti` (JWT ID) permite blacklisting puntual si fuera necesario sin invalidar todos los tokens del usuario.

### 2.5 Almacenamiento en cliente

| Token | Almacenamiento | Justificación |
|---|---|---|
| **Access token** | **Memoria JavaScript** (variable en el closure del módulo de auth) | Evita persistencia entre pestañas/reinicios; no accesible por XSS (localStorage sería vulnerable) |
| **Refresh token** | **Cookie `httpOnly + Secure + SameSite=Strict`** | Inaccesible desde JavaScript (protege contra XSS). `Secure` obliga HTTPS. `SameSite=Strict` elimina riesgo CSRF. |

**Atributos de la cookie de refresh:**
```
Set-Cookie: refresh_token=<valor>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh; Max-Age=604800
```
El `Path` restringido a `/api/auth/refresh` evita que la cookie viaje en cada request de la API.

### 2.6 Política de contraseñas

| Requisito | Valor | Justificación |
|---|---|---|
| Longitud mínima | **8 caracteres** | Según README; aceptable para una app de club |
| Complejidad mínima | **1 mayúscula + 1 número** | Según README |
| Longitud máxima | **128 caracteres** | Previene DoS por hash de passwords largas en BCrypt |
| Hash | **BCrypt, cost factor 12** | Cost 12 ~250ms en hardware moderno (2024); equilibrio entre seguridad y UX |
| Prohibición de reuso | No implementado en v1.0 | Decisión: complejidad no justificada para el contexto |

### 2.7 Bloqueo de cuenta (protección anti-fuerza-bruta)

| Evento | Umbral | Acción | Duración |
|---|---|---|---|
| Intentos fallidos por **IP** | **10 intentos en 10 min** | Bloqueo temporal de la IP en NGINX | 15 minutos |
| Intentos fallidos por **usuario** | **10 intentos en 10 min** | Estado de cuenta `LOCKED` temporal | 15 minutos (automático unlock) |
| Intentos fallidos de **OTP** | **3 intentos** | Invalidar OTP inmediatamente (`used=true`) | Requiere solicitar nuevo OTP |

> El estado `LOCKED` es temporal y se gestiona en cache (no persiste en BD para no contaminar `users.status`). Se libera automáticamente pasados 15 minutos sin necesidad de intervención del admin.

**Implementación en Spring:**
```java
// En AuthApplicationService, antes de BCrypt.checkpw():
loginAttemptService.checkBlocked(ip, login);  // Lanza LoginBlockedException si bloqueado
// Tras fallo:
loginAttemptService.registerFailure(ip, login);
```

### 2.8 Rate limiting por endpoint de auth

| Endpoint | Límite | Por |
|---|---|---|
| `POST /api/auth/login` | 5 req/min | IP |
| `POST /api/auth/register` | 3 req/min | IP |
| `POST /api/auth/password/solicitar-reset` | 3 req/min | IP |
| `POST /api/auth/password/confirmar-reset` | 5 req/10min | IP + email |
| `POST /api/auth/refresh` | 10 req/min | IP |

Implementado en NGINX (`limit_req_zone`) como primera barrera, y en Spring con Bucket4j como segunda barrera (más granular).

### 2.9 Flujo OTP vía Telegram (2FA para operaciones críticas)

El OTP actúa como **segundo factor** para operaciones de alto impacto: confirmación de reserva desde Telegram, cancelación de reserva, y reset de contraseña.

```
1. Usuario solicita operación crítica (crear reserva, cancelar, reset password)
2. Backend → OtpApplicationService.generar(userId, tipo)
   a. Genera código de 6 dígitos (SecureRandom, no Math.random())
   b. Persiste en otp_codes: {user_id, code_HASHED (SHA-256), type, expires_at=now()+10min, used=false}
      ← El código se hashea en BD para proteger contra read-access a la BD
   c. Envía código en claro al Telegram personal del usuario (TelegramClientAdapter)
3. Usuario recibe OTP en Telegram y lo introduce en la app/bot
4. Backend → OtpApplicationService.validar(userId, tipo, codigoIntroducido)
   a. Consulta otp_codes WHERE user_id=? AND type=? AND used=false AND expires_at > now()
   b. Compara SHA-256(codigoIntroducido) con code_hash almacenado
   c. Si válido: UPDATE used=true, procede con la operación
   d. Si inválido: incrementa contador de intentos; tras 3 fallos → UPDATE used=true (invalida)
5. OTP expirado o usado nunca puede reutilizarse
```

> **Nota sobre hasheo del OTP:** Aunque el OTP es de corta duración (10 min), almacenarlo como SHA-256 protege contra un atacante con acceso de lectura a la BD que podría suplantar al usuario durante esa ventana.

**Cómo se prueba:** `api-tester` debe verificar:
- OTP expirado (>10 min) → 422
- OTP ya usado → 422
- OTP de otro usuario → 422 (no filtra si existe)
- Cuarto intento fallido → OTP invalidado automáticamente

---

## 3. Autorización (RBAC)

### 3.1 Roles del sistema

Extraídos del README y backlog:

| Rol | Descripción | Fuente |
|---|---|---|
| `ADMIN` | Administrador del club. Acceso total. Gestiona usuarios, reservas, pagos y configuración. | README §1.4, backlog EP-05 |
| `USER` | Jugador registrado. Gestiona sus propias reservas y pagos. | README §2.x |

> **Roles sin implementar en v1.0 (marcado para validación):** No existe rol `MANAGER_CLUB` o `INVITADO`. El usuario casual (sin cuenta) que participa en reservas via Telegram **no tiene acceso a la API web** y no necesita rol.

### 3.2 Matriz RBAC completa

| Recurso / Acción | PUBLIC | USER | ADMIN | SYSTEM |
|---|:---:|:---:|:---:|:---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | — |
| `POST /api/auth/register` | ✅ | — | — | — |
| `POST /api/auth/refresh` | ✅ | ✅ | ✅ | — |
| `POST /api/auth/logout` | — | ✅ | ✅ | — |
| `POST /api/auth/password/solicitar-reset` | ✅ | ✅ | ✅ | — |
| `POST /api/auth/password/confirmar-reset` | ✅ | ✅ | ✅ | — |
| `GET /api/usuarios/me` | — | ✅ | ✅ | — |
| `PATCH /api/usuarios/me` | — | ✅ | ✅ | — |
| `GET /api/admin/usuarios` | — | ❌ | ✅ | — |
| `POST /api/admin/usuarios` | — | ❌ | ✅ | — |
| `GET /api/admin/usuarios/{id}` | — | ❌ | ✅ | — |
| `PATCH /api/admin/usuarios/{id}` | — | ❌ | ✅ | — |
| `PATCH /api/admin/usuarios/{id}/aprobar` | — | ❌ | ✅ | — |
| `DELETE /api/admin/usuarios/{id}` | — | ❌ | ✅ | — |
| `GET /api/reservas/disponibles` | — | ✅ | ✅ | — |
| `GET /api/reservas` | — | ✅ (propias) | ✅ | — |
| `POST /api/reservas` | — | ✅ | ✅ | — |
| `GET /api/reservas/{id}` | — | ✅ (si owner o participante) | ✅ | — |
| `DELETE /api/reservas/{id}` | — | ✅ (solo si owner) | ✅ | — |
| `POST /api/reservas/{id}/unirse` | — | ✅ | ✅ | — |
| `GET /api/admin/reservas` | — | ❌ | ✅ | — |
| `PATCH /api/admin/reservas/{id}/estado` | — | ❌ | ✅ | — |
| `GET /api/pagos` | — | ✅ (propios) | ✅ | — |
| `POST /api/pagos/iniciar` | — | ✅ (si owner) | ✅ | — |
| `POST /api/admin/pagos/{reservaId}/efectivo` | — | ❌ | ✅ | — |
| `GET /api/admin/pagos` | — | ❌ | ✅ | — |
| `POST /api/otp/verificar` | — | ✅ | ✅ | — |
| `POST /api/bot/telegram` (webhook Telegram) | — | — | — | ✅ (Telegram) |
| `POST /api/pagos/webhook` (webhook Redsys) | — | — | — | ✅ (Redsys) |

**Leyenda:** ✅ = ALLOW · ❌ = DENY explícito · — = No aplica · SYSTEM = validación por header, no JWT

### 3.3 Reglas de negocio que requieren autorización fina (más allá del rol)

Estas reglas no se pueden expresar solo con RBAC y requieren validación a nivel de servicio:

| ID | Regla | Implementación |
|---|---|---|
| **RN-AUTH-01** | Un `USER` solo puede ver (`GET /api/reservas/{id}`) una reserva si es `owner_id` o aparece en `participants` | `ReservaApplicationService.obtenerReserva()` verifica ownership + participación antes de devolver |
| **RN-AUTH-02** | Un `USER` solo puede cancelar (`DELETE /api/reservas/{id}`) su propia reserva (`owner_id = authenticatedUserId`) | `ReservaApplicationService.cancelarReserva()` verifica `reservation.ownerId == authenticatedUserId` |
| **RN-AUTH-03** | Un `USER` no puede unirse dos veces a la misma reserva | `ReservaApplicationService.unirseAReserva()` verifica unicidad en `participants` para ese `user_id` |
| **RN-AUTH-04** | Un `USER` solo puede iniciar pago de una reserva de la que es `owner_id` | `PagoApplicationService.iniciarPago()` verifica `reservation.ownerId == authenticatedUserId` |
| **RN-AUTH-05** | Un `ADMIN` no puede desactivarse a sí mismo | `UsuarioApplicationService.desactivar()` verifica `targetId != authenticatedUserId` |

### 3.4 Implementación técnica en Spring Security

```java
// SecurityConfig.java
http
  .authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/bot/telegram").permitAll()  // Verificado por header, ver §7
    .requestMatchers("/api/pagos/webhook").permitAll()  // Verificado por HMAC, ver §6
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
  )
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

// Autorización fina — en la capa Application (no en Controller):
// NO usar @PreAuthorize con lógica de ownership en Controllers.
// La validación de ownership siempre en el ApplicationService para mantener
// la lógica de negocio en el dominio.
```

---

## 4. Gestión de sesiones

### 4.1 Estrategia: Stateless con JWT + revocación selectiva por refresh token

El acceso es **completamente stateless** a nivel de access token: el backend no mantiene ningún estado de sesión. La revocación granular se gestiona a través del refresh token almacenado en BD.

**Justificación frente a sesión en servidor:** El sistema es de instancia única On-Premise; no hay balanceador que requiera sticky sessions. El modelo stateless simplifica el despliegue Docker Compose y elimina la dependencia de Redis para sesiones.

### 4.2 Revocación de tokens

| Tipo | Revocación |
|---|---|
| **Access token** | No revocable individualmente (stateless). Expiración natural en 15 min. Si se requiere revocación inmediata (ej. logout, cambio de contraseña), el refresh token se invalida y los access tokens quedan "huérfanos" expirando en ≤15 min. Este riesgo es aceptado dado el lifetime corto. |
| **Refresh token** | Revocable individualmente: `UPDATE refresh_tokens SET revoked=true WHERE token_hash=SHA256(token)`. La invalidación es inmediata — el próximo intento de refresh devuelve 401. |

### 4.3 Logout efectivo

```
POST /api/auth/logout:
1. Leer refresh_token de la cookie httpOnly
2. Computar SHA-256(token) → token_hash
3. UPDATE refresh_tokens SET revoked=true WHERE token_hash=?
4. Set-Cookie: refresh_token=; Max-Age=0; HttpOnly; Secure; Path=/api/auth/refresh
5. El access token expira en ≤15 min (riesgo residual aceptado)
```

Para escenarios de compromiso de cuenta (ej. admin detecta sesión sospechosa), el admin puede revocar todos los refresh tokens del usuario:

```sql
UPDATE refresh_tokens SET revoked=true WHERE user_id = :userId;
```

### 4.4 Rotación de refresh tokens

Opcional en v1.0 (se documenta como decisión abierta). Si se implementa: al usar el refresh token, se genera uno nuevo y se invalida el anterior. Detecta reutilización de tokens robados (el segundo uso del token original falla).

---

## 5. Protección frente a OWASP API Security Top 10

### API1 — Broken Object Level Authorization (BOLA)

**Riesgo en PadelPro:** Un `USER` podría acceder a `GET /api/reservas/99` siendo 99 una reserva de otro usuario si el backend no verifica ownership.

**Mitigación:**
- Todos los endpoints con `{id}` en la ruta verifican ownership en la capa Application antes de devolver datos (RN-AUTH-01, RN-AUTH-04).
- Se usa el `userId` del JWT (no un parámetro de la petición) como origen del ownership check.

**Cómo se prueba:** `api-tester` crea reserva con usuario A (id=1), luego intenta `GET /api/reservas/{id}` con token de usuario B. Debe responder 403, no 200 ni 404.

---

### API2 — Broken Authentication

**Riesgo en PadelPro:** Credenciales débiles, tokens de larga duración, almacenamiento inseguro del refresh token.

**Mitigación:**
- BCrypt cost 12 en passwords (§2.6).
- Access token 15 min; refresh token 7 días.
- Refresh token en cookie `httpOnly+Secure+SameSite=Strict` (§2.5).
- Rate limiting + bloqueo anti-fuerza-bruta (§2.7, §2.8).
- `refresh_tokens` almacena hash SHA-256 del token, no el token en claro.

**Cómo se prueba:**
- Intentar 11 logins fallidos seguidos → verificar bloqueo (429 o 403).
- Intentar usar un refresh token revocado → 401.
- Intentar pasar el refresh token como Bearer en Authorization header → 401.

---

### API3 — Broken Object Property Level Authorization

**Riesgo en PadelPro:** Un `USER` podría enviar en `PATCH /api/usuarios/me` el campo `role: "ADMIN"` o `status: "ACTIVE"` y que el backend lo acepte.

**Mitigación:**
- Los DTOs de entrada de endpoints USER usan `@JsonIgnoreProperties` y solo exponen los campos permitidos (`email`, `phone`, `name`, `password`). Spring Jackson no mapea campos no declarados en el DTO.
- La asignación de `role` y `status` solo es posible desde `PATCH /api/admin/usuarios/{id}`.

**Cómo se prueba:** `api-tester` envía `PATCH /api/usuarios/me` con body `{"role":"ADMIN"}` con token USER. Verificar que el rol no cambia en BD.

---

### API4 — Unrestricted Resource Consumption

**Riesgo en PadelPro:** Flood de requests de creación de reservas, generación masiva de OTPs, o paginación sin límites.

**Mitigación:**
- Rate limiting en NGINX y Bucket4j (§2.8).
- Todas las listas devueltas están paginadas (`page + pageSize`, máximo 100 por página).
- La generación de OTP invalida automáticamente el OTP anterior del mismo tipo antes de crear uno nuevo (no se acumulan OTPs activos del mismo tipo).
- El importe del pago siempre se calcula en backend; no hay loop de complejidad O(n) en queries sin límite.

**Cómo se prueba:** Enviar 100 OTPs de tipo RESERVATION_CONFIRM en 1 minuto para el mismo usuario → verificar que el rate limiter actúa.

---

### API5 — Broken Function Level Authorization

**Riesgo en PadelPro:** Un `USER` accede a endpoints `POST /api/admin/usuarios` o `GET /api/admin/pagos`.

**Mitigación:**
- Todos los endpoints bajo `/api/admin/**` requieren `hasRole('ADMIN')` en `SecurityConfig` (§3.4).
- El prefijo `/api/admin/` es la barrera explícita; no hay función admin fuera de ese prefijo.
- Los webhooks (Telegram, Redsys) tienen su propia validación por header, nunca JWT.

**Cómo se prueba:** `api-tester` intenta `GET /api/admin/usuarios` con token USER → debe responder 403, no 200.

---

### API6 — Unrestricted Access to Sensitive Business Flows

**Riesgo en PadelPro:** Un script automatizado reserva todas las franjas disponibles; un actor malicioso genera OTPs masivos para saturar el servicio de Telegram.

**Mitigación:**
- Rate limiting específico en `POST /api/reservas`: 5 req/min/usuario.
- Rate limiting en generación de OTP: 1 OTP por tipo cada 60 segundos por usuario.
- Un usuario `INACTIVE` o `PENDING` no puede crear reservas (validado en `UsuarioApplicationService`).
- El bloqueo de cuenta (§2.7) previene automatización de login.

**Cómo se prueba:** Intentar crear 10 reservas seguidas con el mismo usuario → verificar que el rate limiter actúa tras la 5ª.

---

### API7 — Server Side Request Forgery (SSRF)

**Riesgo en PadelPro:** Bajo. PadelPro no hace requests a URLs controladas por el usuario. Los únicos outbound HTTP son a Redsys (URL hardcodeada en config), Telegram API (URL fija), y SMTP. Sin embargo, el `payment_url` de Redsys se genera en backend y no acepta entrada del usuario para construirlo.

**Mitigación:**
- Las URLs de Redsys y Telegram API son constantes en `application.yml`, nunca provienen de input de usuario.
- El webhook de Redsys que llega al servidor no puede disparar requests salientes desde el backend.
- Whitelist de IPs de Redsys para el endpoint `POST /api/pagos/webhook` en NGINX (ver §6.4).

**Cómo se prueba:** Verificar que ningún endpoint acepta un parámetro `url` o `redirect` que el backend consuma para hacer requests salientes.

---

### API8 — Security Misconfiguration

**Riesgo en PadelPro:** Endpoints de actuator expuestos, Swagger UI en producción, headers HTTP por defecto de Spring Boot sin hardening.

**Mitigación:**
- `Spring Actuator` limitado a `health` y `info` en producción; resto deshabilitados.
- `Swagger UI` (`/swagger-ui.html`) deshabilitado en perfil `prod`; solo disponible en `dev`.
- Cabeceras de seguridad configuradas (ver §8).
- Puerto 5432 (PostgreSQL) no expuesto fuera de la red Docker.
- Puerto 8080 (backend) no expuesto directamente; solo accesible via NGINX en :443.

**Cómo se prueba:** `api-tester` verifica: `/actuator/env` → 404 en prod. `/swagger-ui.html` → 404 en prod. Respuesta incluye cabeceras `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`.

---

### API9 — Improper Inventory Management

**Riesgo en PadelPro:** Versiones anteriores de la API expuestas accidentalmente; el webhook de Telegram accesible sin validación de secret.

**Mitigación:**
- Solo existe `/api/v1/` (o sin versión en v1.0). No hay endpoints legacy.
- El webhook de Telegram (`POST /api/bot/telegram`) solo acepta requests con `X-Telegram-Bot-Api-Secret-Token` válido.
- El webhook de Redsys (`POST /api/pagos/webhook`) solo acepta IPs de Redsys (whitelist en NGINX).
- OpenAPI spec generada automáticamente; solo documenta endpoints activos.

**Cómo se prueba:** `api-tester` verifica que `POST /api/bot/telegram` sin el header secret responde 403.

---

### API10 — Unsafe Consumption of APIs

**Riesgo en PadelPro:** El backend consume Telegram API y Redsys. Si Telegram devuelve datos maliciosos (JSON inesperado), podría causar comportamiento no esperado.

**Mitigación:**
- Las respuestas de Telegram API se deserializan en DTOs tipados con `@JsonIgnoreProperties(ignoreUnknown = true)`. Ningún campo de la respuesta de Telegram se ejecuta como código.
- La respuesta del webhook de Redsys se valida HMAC antes de procesar cualquier campo.
- Timeouts configurados en los clientes HTTP salientes: 5s connect, 10s read (evita que un servicio externo lento bloquee threads).
- El contenido de mensajes Telegram (texto libre del usuario) se sanitiza antes de cualquier uso en queries (prepared statements) o en UI.

**Cómo se prueba:** Enviar un webhook de Telegram con JSON malformado o campos extra → verificar que el backend responde 200 (absorbe el error) sin lanzar excepción no controlada.

---

## 6. Pasarela de pago Redsys

### 6.1 Flujo HMAC SHA-256 paso a paso

Redsys usa un esquema de firma basado en 3DES + HMAC-SHA256. Todo el proceso ocurre **exclusivamente en el backend**.

```
BACKEND (PagoApplicationService):

1. Preparar parámetros de la transacción:
   {
     "DS_MERCHANT_AMOUNT":    "1500",           ← Importe en céntimos (NUMERIC(12,2) × 100)
     "DS_MERCHANT_ORDER":     "ORDER-<uuid>",   ← redsys_order_id (único, UUID truncado a 12 chars)
     "DS_MERCHANT_MERCHANTCODE": "<code>",
     "DS_MERCHANT_TERMINAL":  "001",
     "DS_MERCHANT_TRANSACTIONTYPE": "0",        ← 0 = pago estándar
     "DS_MERCHANT_CURRENCY":  "978",            ← EUR
     "DS_MERCHANT_URLOK":     "https://padelpro.local/pago/ok",
     "DS_MERCHANT_URLKO":     "https://padelpro.local/pago/ko",
     "DS_MERCHANT_URL":       "https://padelpro.local/api/pagos/webhook"
   }

2. DS_MERCHANT_PARAMETERS = Base64(JSON.stringify(params))
   ← Encoding URL-safe, sin padding alterado

3. Derivar clave de sesión Kc:
   Kc = 3DES-CBC(key=Base64Decode(MERCHANT_SECRET_KEY), data=DS_MERCHANT_ORDER)
   ← MERCHANT_SECRET_KEY viene de system_config.redsys_secret_key (descifrado AES-256)

4. Calcular firma:
   DS_SIGNATURE = Base64(HMAC-SHA256(key=Kc, data=DS_MERCHANT_PARAMETERS))

5. Enviar al frontend solo: {DS_MERCHANT_PARAMETERS, DS_SIGNATURE, DS_SIGNATUREVERION="HMAC_SHA256_V1"}
   ← El frontend solo recibe los parámetros firmados para mostrar el formulario de redirección
   ← Nunca se envía el secret al frontend

6. El usuario completa el pago en el TPV virtual de Redsys (redirección externa)
```

### 6.2 Validación del webhook de notificación

```
REDSYS → POST /api/pagos/webhook:
{
  "Ds_SignatureVersion": "HMAC_SHA256_V1",
  "Ds_MerchantParameters": "<base64>",
  "Ds_Signature": "<base64>"
}

BACKEND (PagoWebhookAdapter):

1. VALIDACIÓN ORIGEN (primera barrera):
   - Verificar que la IP del request está en la whitelist de IPs de Redsys (NGINX)
   - Si no pasa: responder 403, registrar en audit_log

2. VALIDACIÓN HMAC (segunda barrera):
   a. Decodificar Ds_MerchantParameters (Base64 → JSON)
   b. Extraer DS_MERCHANT_ORDER del JSON
   c. Derivar Kc = 3DES-CBC(MERCHANT_SECRET_KEY, DS_MERCHANT_ORDER)
   d. Calcular expected_sig = Base64(HMAC-SHA256(Kc, Ds_MerchantParameters))
   e. Comparar Ds_Signature == expected_sig (comparación de strings constante en tiempo)
   f. Si no coincide: responder 200 (no revelar fallo), registrar en audit_log con alerta

3. IDEMPOTENCIA:
   a. Buscar payments WHERE redsys_order_id = Ds_Merchant_Order
   b. Si ya está en status=PAID: responder 200 sin reprocesar (replay attack)
   c. Si no existe: ERROR — inconsistencia (registrar alerta)

4. VALIDACIÓN DE NEGOCIO:
   a. Extraer Ds_Response del JSON (código de respuesta Redsys)
   b. Si Ds_Response IN ('0000'..'0099'): pago aprobado
   c. Cualquier otro valor: pago rechazado/error

5. TRANSICIÓN DE ESTADO (en @Transactional):
   a. UPDATE payments SET status='PAID', paid_at=now(), transaction_id=Ds_AuthorisationCode
   b. INSERT audit_log (action='PAYMENT_CONFIRMED', entity_id=payment_id)
   c. Notify usuario via Telegram (asíncrono, no bloquea la respuesta)

6. Responder 200 OK a Redsys (siempre, incluso en error — Redsys reintentará si no recibe 200)
```

### 6.3 Qué se almacena vs. qué NO (PCI-DSS lite)

PadelPro **no procesa ni almacena datos de tarjeta**. Todo ocurre en el entorno de Redsys.

| Campo | Almacenado | Dónde |
|---|---|---|
| `Ds_Merchant_Order` (referencia de orden) | ✅ Sí | `payments.redsys_order_id` |
| `Ds_AuthorisationCode` (autorización banco) | ✅ Sí | `payments.transaction_id` |
| Número de tarjeta (PAN) | ❌ Nunca | — |
| CVV / CVC | ❌ Nunca | — |
| Fecha de caducidad | ❌ Nunca | — |
| Titular de la tarjeta | ❌ Nunca | — |
| `Ds_MerchantParameters` completo | ⚠️ Solo en audit_log con PII eliminado | `audit_log.details` |

### 6.4 Reconciliación: qué pasa si el webhook no llega

| Escenario | Detección | Acción |
|---|---|---|
| Webhook retrasado (Redsys reintenta hasta 10 veces en 24h) | `payments.status=IN_PROGRESS` y `paid_at IS NULL` pasadas 2h | Job de reconciliación: consulta estado de la orden en Redsys API cada hora |
| Webhook nunca llega tras 24h | `payments.status=IN_PROGRESS` y `created_at < now()-24h` | Alerta al admin en dashboard; el admin puede verificar manualmente y registrar como efectivo |
| Pago aprobado en Redsys pero BD en PENDING | Dashboard de pagos pendientes con fecha antigua | Admin consulta en portal Redsys y actualiza manualmente o espera reconciliación |

---

## 7. Bot Telegram

### 7.1 Verificación de origen de los mensajes

Telegram permite configurar un `secret_token` al registrar el webhook. Este token se envía en el header `X-Telegram-Bot-Api-Secret-Token` en cada llamada al webhook.

```java
// BotTelegramAdapter.java
@PostMapping("/api/bot/telegram")
public ResponseEntity<Void> handleUpdate(
    @RequestHeader("X-Telegram-Bot-Api-Secret-Token") String secretToken,
    @RequestBody TelegramUpdate update) {

  if (!secretToken.equals(appProperties.getTelegramWebhookSecret())) {
    auditService.registrar("TELEGRAM_WEBHOOK_INVALID_SECRET", ...);
    return ResponseEntity.status(403).build();
  }
  // Procesar update
}
```

El `TELEGRAM_WEBHOOK_SECRET` es una cadena aleatoria de 256 bits generada al registrar el bot y almacenada en `system_config.telegram_bot_token` (cifrado). Nunca se expone en logs.

### 7.2 Flujo OTP completo para operaciones vía bot

```
1. Usuario escribe en el GRUPO: "reserva de pista 15/06/2025 18:00 90min"
2. Bot recibe el mensaje (webhook validado por secret_token)
3. BotTelegramAdapter identifica el chat_id del remitente
4. MensajeriaApplicationService busca en users WHERE telegram_chat_id = chat_id
   → Si no existe: responde "Vincula primero tu cuenta en padelpro.local/perfil"
   → Si existe: procede
5. Verifica disponibilidad (ReservaUseCase.listarDisponibles)
6. OtpApplicationService.generar(userId, 'RESERVATION_CONFIRM')
7. TelegramClientAdapter.enviarDirecto(chat_id_del_usuario, "Tu código: XXXXXX")
   ← Envío al CHAT PERSONAL del usuario, no al grupo
8. Bot responde en el grupo: "✅ Código enviado a tu Telegram personal. Tienes 10 minutos."
9. Usuario responde en el grupo: el OTP
10. Bot valida OTP (OtpApplicationService.validar)
11. Si válido: INSERT reserva, publica confirmación en el grupo
12. Si inválido (3 intentos): OTP invalidado, respuesta de error en el grupo
```

### 7.3 Vinculación cuenta PadelPro ↔ chat_id Telegram

La vinculación es el paso de seguridad más delicado: garantiza que el `chat_id` de Telegram corresponde a la cuenta correcta de PadelPro.

**Flujo de vinculación:**

```
1. Usuario accede a su perfil en la web (autenticado con JWT)
2. Pulsa "Vincular Telegram"
3. Backend genera un código de vinculación temporal (6 dígitos, TTL 15 min):
   INSERT otp_codes (user_id, code_hash, type='TELEGRAM_LINK', expires_at=now()+15min)
4. La web muestra: "Envía este código al bot @PadelProBot: /vincular XXXXXX"
5. Usuario abre Telegram y envía: /vincular XXXXXX al bot (@PadelProBot)
6. Bot recibe el chat_id del usuario y el código
7. Backend valida el OTP tipo TELEGRAM_LINK:
   SELECT ... WHERE code_hash=SHA256(XXXXXX) AND type='TELEGRAM_LINK' AND used=false AND expires_at>now()
8. Si válido:
   UPDATE users SET telegram_chat_id=:chatId, telegram_linked_at=now() WHERE id=:userId
   UPDATE otp_codes SET used=true WHERE id=:otpId
   Bot responde: "✅ Cuenta vinculada correctamente"
9. Si ya existe otro usuario con ese chat_id:
   Bot responde: "Este Telegram ya está vinculado a otra cuenta. Contacta con el administrador."
```

**Revocación de la vinculación:**

```
PATCH /api/usuarios/me con body {"telegramUnlink": true}
→ UPDATE users SET telegram_chat_id=NULL, telegram_linked_at=NULL WHERE id=:userId
→ Invalida todos los OTP activos del usuario
→ Registra en audit_log
```

### 7.4 Comandos sensibles en el grupo y validación de permisos

| Comando en el grupo | Validación requerida |
|---|---|
| `reserva de pista DD/MM/AA HH:MM dur` | `telegram_chat_id` vinculado + `users.status=ACTIVE` |
| `cancelacion reserva DD/MM/AA HH:MM` | `telegram_chat_id` vinculado + `owner_id` de la reserva = `user_id` de la cuenta |
| Unirse a reserva (respuesta al mensaje del grupo) | Sin cuenta requerida; solo `external_name` registrado |

El bot **nunca ejecuta acciones administrativas** (crear usuarios, cambiar precios, etc.) independientemente de quién escriba en el grupo. Los comandos admin solo están disponibles en la interfaz web con autenticación JWT.

---

## 8. CORS, CSP y cabeceras de seguridad

### 8.1 Política CORS

```java
// CorsConfig.java (Spring)
@Bean
public CorsConfigurationSource corsConfigurationSource() {
  CorsConfiguration config = new CorsConfiguration();

  // Orígenes por entorno (inyectados desde application.yml)
  config.setAllowedOrigins(allowedOrigins);  // Ver tabla inferior
  config.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS"));
  config.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With"));
  config.setAllowCredentials(true);  // Necesario para cookies httpOnly
  config.setMaxAge(3600L);

  UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
  source.registerCorsConfiguration("/api/**", config);
  return source;
}
```

| Entorno | Orígenes permitidos |
|---|---|
| **DES** | `http://localhost:3000`, `http://localhost:5173` |
| **PRE** | `https://pre.padelpro.local` |
| **PRO** | `https://padelpro.local` |

> Los webhooks de Telegram y Redsys **no tienen restricción CORS** (son llamadas server-to-server, no requests de navegador).

### 8.2 Cabeceras de seguridad HTTP

Configuradas en NGINX (primera barrera) y en Spring Security (segunda barrera):

```nginx
# nginx.conf — bloque server
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'self' https://sis-t.redsys.es https://sis.redsys.es;" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), camera=(), microphone=()" always;
```

| Cabecera | Valor | Protección |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Fuerza HTTPS durante 1 año |
| `Content-Security-Policy` | Ver arriba | XSS, clickjacking, inyección de recursos |
| `X-Content-Type-Options` | `nosniff` | MIME sniffing |
| `X-Frame-Options` | `DENY` | Clickjacking |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Fuga de URLs privadas |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` | APIs de navegador no necesarias |

**Nota CSP para Redsys:** El `form-action` incluye los dominios de Redsys (`sis-t.redsys.es` para tests, `sis.redsys.es` para producción) porque el pago se inicia con un formulario POST redirigido.

---

## 9. Cifrado

### 9.1 En tránsito

- **TLS 1.2 mínimo, 1.3 preferido.** Configurado en NGINX.
- **HSTS** activo con `preload` (ver §8.2).
- Certificado SSL: **Let's Encrypt** vía Certbot, renovación automática.
- Comunicación entre contenedores Docker (backend ↔ PostgreSQL): red Docker interna; no requiere TLS (mismo host).
- Comunicación saliente (backend → Telegram API, backend → Redsys): HTTPS obligatorio; el cliente HTTP valida el certificado del servidor (no deshabilitar `ssl.verify=false`).

**Suites de cifrado TLS permitidas en NGINX:**

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305';
ssl_prefer_server_ciphers off;
```

### 9.2 En reposo

| Dato | Cifrado | Implementación |
|---|---|---|
| `system_config.redsys_secret_key` | AES-256-GCM | `@Convert(converter = EncryptedStringConverter.class)` en JPA |
| `system_config.telegram_bot_token` | AES-256-GCM | Idem |
| `system_config.smtp_password` | AES-256-GCM | Idem |
| `users.password_hash` | BCrypt | BCrypt no es reversible; no requiere gestión de clave |
| `otp_codes.code_hash` | SHA-256 | Hash de un solo sentido; no requiere gestión de clave |
| `refresh_tokens.token_hash` | SHA-256 | Idem |
| Resto de la BD | Sin cifrado de columna | Protección por acceso restringido al servidor (red Docker) |

**Clave de cifrado AES-256:** Almacenada en `APP_ENCRYPTION_KEY` (variable de entorno del contenedor backend). Generada con `openssl rand -base64 32`. Nunca en el código ni en archivos commiteados.

**Cifrado de BD a nivel de tablespace:** No implementado en v1.0 (se confía en el control de acceso al servidor On-Premise). Si el servidor es compartido o el riesgo físico es relevante, usar `pgcrypto` o cifrado de disco completo (luks/dm-crypt en el host Linux).

### 9.3 Gestión de secrets

| Secret | Almacenamiento DES | Almacenamiento PRO |
|---|---|---|
| `APP_JWT_SECRET` | `.env` (no commiteado, en `.gitignore`) | Variable de entorno del container (Docker Compose `.env` en servidor, acceso restringido) |
| `APP_ENCRYPTION_KEY` | `.env` | Idem |
| `REDSYS_SECRET_KEY` | `system_config` en BD (cifrado AES) | Idem |
| `TELEGRAM_BOT_TOKEN` | `system_config` en BD (cifrado AES) | Idem |
| `SMTP_PASSWORD` | `system_config` en BD (cifrado AES) | Idem |
| `DB_PASSWORD` | `.env` | Variable de entorno del container |

**Reglas de secrets:**
1. Ningún secret en código fuente ni en repositorio Git.
2. `.env` en `.gitignore`; `.env.example` con placeholders en el repo.
3. Rotación semestral de `APP_JWT_SECRET` (invalida todos los refresh tokens activos; usuarios deben re-autenticarse).
4. Rotación de `REDSYS_SECRET_KEY` coordinada con el banco.

---

## 10. Auditoría y logging

### 10.1 Eventos auditados

Todos los eventos se persisten en la tabla `audit_log` (ver `docs/data-model.md §3.7`).

| Evento | `action` en audit_log | Prioridad |
|---|---|---|
| Login exitoso | `USER_LOGIN_SUCCESS` | Alta |
| Fallo de login | `USER_LOGIN_FAILED` | Alta |
| Bloqueo de cuenta | `USER_ACCOUNT_LOCKED` | Alta |
| Registro de usuario | `USER_REGISTERED` | Alta |
| Aprobación de cuenta (admin) | `USER_APPROVED` | Alta |
| Desactivación de usuario (admin) | `USER_DEACTIVATED` | Alta |
| Cambio de contraseña | `PASSWORD_CHANGED` | Alta |
| Reset de contraseña | `PASSWORD_RESET` | Alta |
| Cambio de rol (admin) | `USER_ROLE_CHANGED` | Alta |
| Vinculación Telegram | `TELEGRAM_LINKED` | Media |
| Desvinculación Telegram | `TELEGRAM_UNLINKED` | Media |
| OTP generado | `OTP_GENERATED` | Media |
| OTP validado (OK) | `OTP_VALIDATED` | Media |
| OTP fallido | `OTP_FAILED` | Alta |
| Creación de reserva | `RESERVATION_CREATED` | Media |
| Cancelación de reserva | `RESERVATION_CANCELLED` | Media |
| Unión a reserva | `RESERVATION_JOINED` | Media |
| Cambio de estado de reserva (admin) | `RESERVATION_STATUS_CHANGED` | Alta |
| Inicio de pago | `PAYMENT_INITIATED` | Alta |
| Confirmación de pago (webhook) | `PAYMENT_CONFIRMED` | Alta |
| Pago rechazado (webhook) | `PAYMENT_REJECTED` | Alta |
| Pago efectivo registrado (admin) | `PAYMENT_CASH_REGISTERED` | Alta |
| Webhook Redsys con firma inválida | `PAYMENT_WEBHOOK_INVALID_SIGNATURE` | Crítica |
| Webhook Telegram con secret inválido | `TELEGRAM_WEBHOOK_INVALID_SECRET` | Crítica |
| Actualización de system_config | `CONFIG_UPDATED` | Alta |
| Anonimización de usuario (RGPD) | `USER_ANONYMIZED` | Crítica |
| Acceso admin a datos de usuario | `ADMIN_USER_DATA_ACCESS` | Alta |

### 10.2 Qué sí / qué NO se loguea

| Categoría | ¿Se loguea? | Razón |
|---|---|---|
| Contraseñas (en claro o hash) | ❌ Nunca | Dato sensible; su log constituye brecha |
| Tokens JWT (access o refresh) | ❌ Nunca | Si el log se compromete, los tokens son válidos |
| Códigos OTP en claro | ❌ Nunca | Equivalente a publicar el segundo factor |
| `redsys_secret_key`, `telegram_bot_token` | ❌ Nunca | Credenciales críticas |
| PAN / CVV (nunca llegan al backend) | ❌ Nunca | PCI-DSS |
| IPs de origen | ✅ Sí | Investigación de incidentes; retención 2 años |
| User-Agent | ✅ Sí | Contexto de incidentes |
| `entity_id` de la entidad afectada | ✅ Sí | Trazabilidad |
| Detalle de cambios (JSON diff) | ✅ Sí, sin datos sensibles | Trazabilidad en `audit_log.details` |
| Respuesta de la pasarela Redsys | ✅ `Ds_Response` y `Ds_AuthorisationCode` | Reconciliación financiera |

### 10.3 Logging en aplicación (SLF4J + Logback)

```
Niveles por entorno:
  DES:  DEBUG
  PRE:  INFO
  PRO:  WARN (+ ERROR siempre)

NO usar Logger.debug() para datos de usuario en código de producción.
Usar MDC para propagar userId, requestId en todos los logs del request.
```

### 10.4 Centralización de logs

En v1.0 On-Premise: logs de Docker Compose en `/var/log/padelpro/` con rotación diaria (7 días de retención en fichero, 2 años en `audit_log` de BD).

Si se escala: ELK stack o Loki/Grafana en el mismo servidor. No se envían logs a servicios cloud externos en v1.0 (datos personales on-premise).

---

## 11. RGPD

### 11.1 Datos personales identificados

Referencia cruzada con `docs/data-model.md §8`:

| Tabla | Campo | Categoría RGPD | Base legal |
|---|---|---|---|
| `users` | `first_name`, `last_name` | Dato identificativo | Ejecución de contrato |
| `users` | `phone` | Dato de contacto | Ejecución de contrato + interés legítimo (OTP) |
| `users` | `email` | Dato de contacto | Ejecución de contrato |
| `users` | `telegram_chat_id` | Identificador de cuenta de mensajería | Consentimiento (vinculación voluntaria) |
| `users` | `password_hash` | Credencial de acceso | Ejecución de contrato |
| `participants` | `external_name` | Dato de tercero (jugador casual) | Interés legítimo mínimo |
| `participants` | `external_phone` | Dato de contacto de tercero | Interés legítimo mínimo |
| `audit_log` | `ip_address` | Dato de red | Interés legítimo (seguridad) |
| `notification_log` | `recipient`, `message` | Dato de comunicación | Ejecución de contrato |

### 11.2 Derechos del titular y procedimiento

| Derecho (Art. RGPD) | Endpoint / Procedimiento | SLA de respuesta |
|---|---|---|
| **Acceso** (Art. 15) | `GET /api/usuarios/me` devuelve datos propios. Para datos de `audit_log` y `notification_log`: proceso manual vía email a admin. | 30 días |
| **Rectificación** (Art. 16) | `PATCH /api/usuarios/me` — nombre, email, teléfono. | Inmediato vía UI |
| **Supresión** (Art. 17) | Proceso de anonimización definido en `docs/data-model.md §8.3`. Endpoint: `DELETE /api/admin/usuarios/{id}` → anonimiza campos personales, no borra reservas/pagos (obligación fiscal). | 30 días |
| **Portabilidad** (Art. 20) | No hay endpoint en v1.0. Proceso manual: admin exporta `SELECT` de datos del usuario en CSV. **Decisión abierta P-RGPD-01.** | 30 días |
| **Oposición al tratamiento** (Art. 21) | Desvinculación de Telegram: `PATCH /api/usuarios/me`. Desactivación de cuenta: contactar al admin. | Variable |
| **Limitación del tratamiento** (Art. 18) | Proceso manual: admin marca el usuario como `INACTIVE` y no procesa sus datos. | Variable |

### 11.3 Política de retención

Ver `docs/data-model.md §8.2` para la tabla completa. Resumen de categorías RGPD:

| Categoría | Retención | Base |
|---|---|---|
| Datos de cuenta (`users`) | Vida de la cuenta + 5 años tras inactivación | Prescripción civil |
| Reservas y pagos | 5 años | Obligación fiscal (AEAT) |
| Logs de auditoría | 2 años | Interés legítimo seguridad + RGPD art. 5.1.e |
| Logs de notificación | 2 años | Interés legítimo |
| OTP codes | 7 días tras expiración | Datos técnicos mínimos |

### 11.4 Encargados de tratamiento

| Encargado | Servicio | Datos transferidos | Medida contractual |
|---|---|---|---|
| **Telegram** (Telegram FZ-LLC, Dubái) | Bot API — OTP, notificaciones | Número de teléfono, contenido de mensajes | Términos de servicio Telegram (no DPA disponible para bots gratuitos). **Riesgo declarado.** |
| **Redsys** (Red Nacional de Intercambio, S.A.) | Pasarela de pago | Solo importe y referencia de pedido. PadelPro **no transfiere datos de tarjeta**. | Contrato de adhesión Redsys + medidas PCI-DSS del banco |
| **Hosting On-Premise** | Servidor del cliente | Todos los datos | El cliente es el responsable del tratamiento; no hay tercero aquí |
| **SMTP** (servidor de email) | Notificaciones email | Email del usuario, contenido del mensaje | Contrato de servicio SMTP |

### 11.5 Registro de actividades de tratamiento (RAT)

El responsable del tratamiento (el club) debe mantener el RAT según Art. 30 RGPD. PadelPro genera los datos; el club es el responsable. Se recomienda que el admin rellene el RAT con las actividades: gestión de reservas, gestión de pagos, comunicaciones con jugadores.

---

## 12. Vulnerabilidades del stack

### 12.1 Java 21 + Spring Boot 3.2

**Gestión de dependencias:**
- `OWASP Dependency-Check` integrado en el pipeline Maven (`mvn dependency-check:check`). Falla el build si hay CVE Critical/High sin suprimir justificación.
- `Dependabot` en GitHub: PRs automáticas para actualizaciones de seguridad.

**CVEs relevantes conocidos (2024):**
- Spring Framework < 6.1.x: vulnerabilidades en serialización y SPEL. Spring Boot 3.2 usa Spring Framework 6.1.x — mantener actualizado.
- Log4j: PadelPro usa **SLF4J + Logback** (no Log4j). No afectado por Log4Shell.
- Netty (si se usa WebFlux): PadelPro usa Tomcat (Spring MVC) — revisar si se añade WebFlux en el futuro.

**Configuraciones de hardening Spring:**
```yaml
spring:
  jpa:
    open-in-view: false  # Evita LazyLoadingException fuera de transacción + SqlInjection vectores
  mvc:
    pathmatch:
      use-suffix-pattern: false  # Deshabilitar extensiones en URLs (CVE-2022-22965 Spring4Shell)
management:
  endpoints:
    web:
      exposure:
        include: "health,info"  # Solo estos dos en producción
  endpoint:
    health:
      show-details: "when_authorized"
```

### 12.2 React 18

**XSS:**
- React escapa el contenido de JSX por defecto. **Nunca usar `dangerouslySetInnerHTML`** salvo contenido propio sanitizado con `DOMPurify`.
- El contenido de mensajes de Telegram que llega al frontend (nombre de participante, etc.) siempre se renderiza como texto React, no como HTML.
- `npm audit` en CI; fallar pipeline si hay vulnerabilidades High/Critical.

**Dependencias:**
- Dependabot activo para el frontend también.
- No usar `eval()` ni `new Function()` en ningún lugar del código.

**Almacenamiento seguro:**
- Nunca `localStorage.setItem('token', ...)`. Access token en memoria únicamente.
- Nada sensible en `sessionStorage` (accesible por scripts del mismo origen).

### 12.3 PostgreSQL 15

**Hardening básico:**

```sql
-- Crear usuario de aplicación con permisos mínimos
CREATE ROLE padelpro_app LOGIN PASSWORD 'STRONG_PASSWORD_FROM_ENV';
GRANT CONNECT ON DATABASE padelpro TO padelpro_app;
GRANT USAGE ON SCHEMA public TO padelpro_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO padelpro_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO padelpro_app;

-- El usuario de la aplicación NO tiene SUPERUSER ni CREATEDB
-- El usuario postgres (superuser) solo se usa para migraciones Flyway
```

**Red:**
```yaml
# docker-compose.yml — PostgreSQL no expone puerto al host
db:
  image: postgres:15
  networks:
    - padelpro-network
  # NO mapear puertos al host: sin "ports: - 5432:5432"
```

**Backups:**
- `pg_dump` diario (cron en el host) al directorio `/backups/padelpro/`.
- Retención 7 días local.
- Copia semanal a almacenamiento externo (USB, NAS, o cloud) recomendada.

---

## 13. Plan de respuesta a incidentes

### 13.1 Criterios de activación

| Evento | Nivel | Activación |
|---|---|---|
| Acceso no autorizado a datos de usuarios | Crítico | Inmediata |
| Pago procesado con firma HMAC inválida | Crítico | Inmediata |
| Token de bot Telegram comprometido | Alto | < 1h |
| Ataque de fuerza bruta detectado (>100 IPs bloqueadas) | Medio | < 4h |
| Dependencia con CVE Critical sin parchear | Medio | < 24h |
| Fallo de backup 3 días consecutivos | Bajo | < 24h |

### 13.2 Procedimiento (primeras 24 horas)

```
FASE 1 — CONTENCIÓN (0-2h):
  1. Identificar el vector de ataque (logs de auditoría, NGINX access log)
  2. Si hay brecha de datos: desconectar temporalmente el servicio (docker-compose stop backend)
  3. Si hay token comprometido:
     a. JWT: rotar APP_JWT_SECRET (invalida todos los tokens)
     b. Telegram bot: generar nuevo token en @BotFather, actualizar system_config
     c. Redsys: contactar al banco para revocar y regenerar el secret
  4. Revocar todos los refresh_tokens: UPDATE refresh_tokens SET revoked=true

FASE 2 — EVALUACIÓN (2-8h):
  5. Determinar alcance: ¿qué datos han sido accedidos/exfiltrados?
  6. Revisar audit_log del periodo de la brecha
  7. Identificar usuarios afectados
  8. Documentar el incidente (timeline, evidencias)

FASE 3 — NOTIFICACIÓN (RGPD Art. 33/34):
  9. Si hay brecha de datos personales:
     a. Notificar a la AEPD en < 72h desde detección (formulario en aepd.es)
     b. Evaluar si el riesgo es alto → notificar también a los afectados (Art. 34)
  10. Comunicar internamente al responsable del club

FASE 4 — RECUPERACIÓN (8-24h):
  11. Aplicar el parche o corrección
  12. Restaurar desde backup si es necesario
  13. Verificar integridad de datos antes de reactivar el servicio
  14. Monitorizar de forma intensiva las primeras 48h post-incidente

FASE 5 — LECCIONES APRENDIDAS (post-incidente):
  15. Documentar el incidente completo
  16. Actualizar este plan de respuesta
  17. Verificar si se requieren controles adicionales
```

### 13.3 Contactos y referencias

| Recurso | URL / Contacto |
|---|---|
| AEPD — Notificación de brecha | https://sedeagpd.gob.es/sede-electronica-web/ |
| Redsys — Soporte urgente | Contacto del banco gestor del comercio |
| Telegram — Compromiso de bot | @BotFather (regenerar token), reportar en security@telegram.org |
| Let's Encrypt — Cert revocación | `certbot revoke --cert-path /etc/letsencrypt/live/...` |

---

## 14. Decisiones abiertas

Las siguientes decisiones requieren confirmación del propietario del producto o del cliente antes de implementar:

| # | Decisión | Impacto | Acción requerida |
|---|---|---|---|
| **D-SEC-01** | **Rotación de refresh tokens**: ¿implementar en v1.0? Si se implementa, cada uso del refresh token genera uno nuevo e invalida el anterior. Detecta reutilización de tokens robados pero complica la lógica de cliente. | Seguridad vs. complejidad de implementación | Confirmar si el nivel de seguridad adicional justifica el esfuerzo en v1.0 |
| **D-SEC-02** | **OTP hasheado en BD**: El prompt del data-model indica que el OTP se almacena en claro (campo `code VARCHAR(6)`). Este documento propone almacenarlo como SHA-256. ¿Confirmamos el almacenamiento como hash? | Protege contra acceso de lectura a BD; implica actualizar el data-model | Confirmar y actualizar `docs/data-model.md` si se aprueba |
| **D-SEC-03** | **Portabilidad de datos (Art. 20 RGPD)**: No hay endpoint de exportación en v1.0. ¿Se implementa un `GET /api/usuarios/me/exportar` que devuelva un JSON con todos los datos del usuario? | Cumplimiento RGPD; esfuerzo ~3 SP | Confirmar si es requisito para la fecha de lanzamiento |
| **D-SEC-04** | **Whitelist de IPs de Redsys en NGINX**: Las IPs de los servidores de notificación de Redsys son proporcionadas por el banco y pueden cambiar. ¿Se gestiona como variable de entorno o se externaliza la validación al filtro HMAC exclusivamente? | Si las IPs cambian sin actualizar la whitelist, los webhooks fallan | Coordinar con el banco la lista de IPs y la política de cambio |
| **D-SEC-05** | **Telegram DPA**: Telegram no ofrece DPA (Data Processing Agreement) para bots gratuitos. El envío de OTPs implica transferencia de datos personales (número de teléfono como identificador implícito) a Telegram FZ-LLC (Dubái). ¿El club acepta este riesgo explícitamente? | Riesgo legal RGPD transferencia internacional | Decisión del responsable del tratamiento (el club) documentada por escrito |
| **D-SEC-06** | **Account lockout vs. CAPTCHA**: El bloqueo temporal de cuenta (§2.7) puede usarse como vector DoS contra usuarios específicos (un atacante bloquea la cuenta del admin). ¿Se prefiere CAPTCHA en el login en lugar de bloqueo de cuenta? | UX vs. seguridad | Confirmar preferencia; el CAPTCHA es más robusto frente al DoS dirigido |
| **D-SEC-07** | **Lifetime del access token**: Este documento propone 15 min. El README indica 8h. ¿Se confirma el cambio a 15 min? Si se elige 8h, la ventana de exposición ante robo de token es mayor. | Seguridad vs. UX (renovación transparente necesaria en frontend) | Confirmar el lifetime definitivo |
