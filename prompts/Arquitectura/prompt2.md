Actúa como backend-architect. Tu tarea es generar `docs/data-model.md` desde
cero para el proyecto PadelPro.

## Paso 0 — Contexto obligatorio
Antes de escribir nada, lee en este orden:
1. `docs/PROJECT.md` — variables operativas, stack, convenciones.
2. `README.md` — especificación funcional y técnica completa.
3. `backlog.md` — Épicas, Features, US y Tickets (te dice qué entidades
   necesitan existir).
4. Si existe `openspec/project.md`, léelo también para alinear glosario.

Si alguno de esos ficheros no existe o está incompleto, detente y pídeme
qué hacer antes de inventar nada.

## Objetivo
Producir el modelo de datos completo del dominio PadelPro listo para que:
- `backend-architect` lo use como base para mapear entidades JPA.
- `database-optimizer` lo audite (índices, FKs, N+1).
- `api-tester` y `frontend-engineer` deriven DTOs.
- `security-auditor` valide trazabilidad y campos de auditoría.

## Estructura obligatoria del documento

### 1. Resumen ejecutivo
Tabla con todas las entidades del dominio, su responsabilidad en una frase y
la épica de `backlog.md` a la que pertenecen.

### 2. Diagrama ER
Diagrama en formato Mermaid (`erDiagram`) que muestre todas las entidades,
sus PKs, FKs y cardinalidades. Debe ser legible y completo, no parcial.

### 3. Especificación entidad por entidad
Para cada entidad, una sección con:
- **Propósito** (1-2 líneas).
- **Tabla SQL**: nombre exacto en `snake_case`.
- **Columnas**: tabla con `nombre | tipo SQL (PostgreSQL 15) | nullable | default | constraint | descripción`.
- **PK**: explícita.
- **FKs**: tabla con `columna | referencia | on_delete | on_update`.
- **Índices**: tabla con `nombre | columnas | tipo (BTREE/GIN/UNIQUE) | justificación`.
  Regla: toda FK debe tener índice. Toda columna usada en filtros frecuentes,
  también.
- **Constraints de negocio**: CHECKs, UNIQUEs compuestos, exclusion constraints.
- **Campos de auditoría**: `created_at`, `updated_at`, `created_by`, `updated_by`
  cuando proceda. Justifica si una entidad NO los tiene.
- **Enums**: declarados como tipos enum de PostgreSQL, no como VARCHAR libre.

### 4. Relaciones y reglas de integridad
Sección que documente las relaciones no triviales: cascadas, soft-delete,
versiones, snapshots históricos (relevante para pagos Redsys y reservas).

### 5. Estrategia de migraciones (Flyway)
- Convención de nombres `V<n>__<descripcion>.sql`.
- Reglas para migraciones zero-downtime (CONCURRENTLY, columnas nullable
  primero, backfill, NOT NULL después).
- Política de migraciones reversibles.

### 6. Datos seed
Lista de datos seed mínimos para arrancar la app en DES (roles, configuración
del club, tipos de pista, etc.). Indica el fichero Flyway donde irían.

### 7. Consideraciones de rendimiento conocidas
- Consultas previstas con alto volumen (listado de partidas abiertas,
  búsqueda de pista disponible en franja horaria, histórico de pagos).
- Índices que las soportan.
- Riesgos de N+1 conocidos y cómo mitigarlos (JOIN FETCH, @EntityGraph).

### 8. Cumplimiento normativo
- Campos sujetos a RGPD (datos personales) marcados explícitamente.
- Política de retención por tabla.
- Estrategia de borrado/anonimización.

## Reglas de construcción

1. **Deriva el modelo del backlog, no lo inventes.** Cada entidad debe poder
   trazarse a una o más US/Tickets. Si una US sugiere una entidad que no
   tienes, créala. Si una entidad no aparece en ninguna US, justifícala o
   elimínala.
2. **Nombres en español** si `README.md` usa nombres en español; si no, en
   inglés. Sé consistente.
3. **PostgreSQL 15** como motor (lo declara `docs/PROJECT.md`). No uses
   features SQL Server o MySQL.
4. **Enums como tipos PostgreSQL** (`CREATE TYPE ... AS ENUM`), no VARCHAR.
5. **IDs**: decide explícitamente `BIGSERIAL` vs `UUID`. Justifica la elección
   para el dominio (sugerencia: UUID para entidades expuestas externamente
   como reservas y pagos; BIGSERIAL para internas).
6. **Toda FK con índice.** Sin excepciones.
7. **Campos monetarios**: `NUMERIC(12,2)` siempre. Nunca `FLOAT` ni `DOUBLE`.
8. **Timestamps**: `TIMESTAMPTZ` siempre, nunca `TIMESTAMP` sin zona.

## Entregable

Crea `docs/data-model.md` siguiendo la estructura anterior. Al final, añade
una sección "Pendiente / abierto" listando cualquier decisión de modelado
que no hayas podido cerrar y necesite mi input.

No escribas migraciones Flyway aún. Eso es trabajo posterior. Aquí solo
documentas el modelo.

Cuando termines, devuélveme un resumen de 10-15 líneas con: entidades
creadas, decisiones de modelado relevantes (UUID vs BIGSERIAL, soft-delete,
versionado), y dudas abiertas. Espera mi OK antes de proponer commit.


--------------------------------


Actúa como security-auditor. Tu tarea es generar `docs/security-design.md`
desde cero para el proyecto PadelPro.

## Paso 0 — Contexto obligatorio
Antes de escribir nada, lee en este orden:
1. `docs/PROJECT.md` — variables operativas, stack, identidad del bot.
2. `README.md` — especificación funcional y técnica completa.
3. `docs/data-model.md` — entidades, especialmente usuarios, roles, pagos.
4. `backlog.md` — para identificar flujos sensibles (pagos Redsys, bot
   Telegram con OTP, datos personales).

Si `docs/data-model.md` no existe, detente. No puedes diseñar seguridad
sin saber qué proteges.

## Objetivo
Producir el diseño de seguridad completo de PadelPro, listo para que:
- `backend-architect` implemente el módulo de auth.
- `api-tester` derive pruebas OWASP API Top 10.
- `verification-specialist` y `reality-checker` validen contra él.
- `security-auditor` mismo audite implementaciones futuras.

## Estructura obligatoria del documento

### 1. Modelo de amenazas
Análisis STRIDE resumido sobre los activos críticos:
- Cuentas de usuario y credenciales.
- Reservas (no repudio).
- Pagos Redsys (integridad y confidencialidad).
- Bot Telegram (suplantación, OTP).
- Datos personales (RGPD).

Tabla `Activo | Amenaza STRIDE | Vector | Mitigación`.

### 2. Autenticación
- Mecanismo: JWT (access + refresh) salvo justificación contraria.
- Algoritmo de firma: RS256 (asimétrico) o HS256 con secret en vault.
  Decide y justifica.
- Lifetimes: access token (15 min sugerido), refresh token (7-30 días).
- Almacenamiento en cliente: cookie httpOnly+Secure+SameSite=Strict para
  refresh, memoria para access. Justifica.
- Endpoints: `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`,
  `POST /auth/register` (si aplica), `POST /auth/forgot-password`,
  `POST /auth/reset-password`.
- Rate limiting por endpoint de auth (intentos/minuto/IP y por usuario).
- Política de contraseñas: longitud mínima, complejidad, hashing
  (bcrypt cost ≥12 o argon2id).
- Bloqueo de cuenta tras N intentos fallidos.
- 2FA / OTP vía Telegram bot: flujo completo paso a paso.

### 3. Autorización (RBAC)
- Roles del sistema. Extráelos del backlog y del README. Si no están claros,
  propón un set inicial mínimo (ej. `ADMIN`, `MANAGER_CLUB`, `JUGADOR`,
  `INVITADO`) y márcalo para validación.
- **Matriz RBAC completa**: filas = recursos/acciones, columnas = roles,
  celdas = ALLOW/DENY. Debe cubrir todos los endpoints previstos.
- Reglas de negocio críticas (RN-xx) que requieren autorización fina más
  allá del rol (ej. "un jugador solo puede cancelar SUS reservas").
- Estrategia técnica: anotaciones Spring Security (`@PreAuthorize`) +
  validación a nivel de servicio para ownership.

### 4. Gestión de sesiones
- Stateless con JWT vs sesión en servidor. Decide.
- Revocación de tokens (blacklist en Redis, jti, o rotación de claves).
- Logout efectivo: cómo invalidar tokens vivos.

### 5. Protección frente a OWASP API Top 10
Una subsección por cada item (API1 a API10) con: descripción del riesgo en
contexto PadelPro, mitigación implementada, cómo se verifica.

### 6. Pasarela de pago Redsys
- Flujo HMAC SHA-256 paso a paso.
- Generación de la firma en backend (nunca en frontend).
- Validación del webhook de notificación: verificación HMAC, idempotencia,
  protección contra replay.
- Almacenamiento de transacciones: qué se guarda, qué no (PCI-DSS).
- Reconciliación: qué pasa si el webhook no llega.

### 7. Bot Telegram
- Verificación de origen de los mensajes (token bot, secret del webhook).
- Flujo OTP: generación, expiración (ej. 5 min), un solo uso, almacenamiento
  hasheado en BD.
- Vinculación cuenta PadelPro ↔ chat_id Telegram: cómo se inicia y cómo se
  revoca.
- Comandos sensibles en grupo: validación de permisos.

### 8. CORS, CSP y cabeceras
- Política CORS: orígenes permitidos por entorno (DES/PRE/PRO).
- Cabeceras de seguridad obligatorias: `Strict-Transport-Security`,
  `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, `Permissions-Policy`.
- Configuración en Spring Security y en el proxy/CDN.

### 9. Cifrado
- En tránsito: TLS 1.2 mínimo, 1.3 preferido. HSTS.
- En reposo: cifrado de BD a nivel de tablespace y/o columnas sensibles
  (cuál y cómo).
- Secrets: ubicación (Vault, GitHub Secrets, `.env` local), rotación,
  nunca en código.

### 10. Auditoría y logging
- Eventos a auditar (login, fallo de login, cambio de rol, pago, cancelación
  de reserva, acceso a datos personales de otro usuario).
- Tabla de auditoría: campos, retención (mínimo RGPD).
- Logging: qué SÍ se loguea, qué NO (nunca passwords, tokens, PAN, CVV).
- Centralización de logs (cuando aplique).

### 11. RGPD
- Datos personales identificados (cruzar con `docs/data-model.md`).
- Derechos del titular: acceso, rectificación, supresión, portabilidad.
  Endpoint o procedimiento para cada uno.
- Política de retención por categoría de dato.
- Estrategia de anonimización vs borrado.
- Encargados de tratamiento (Redsys, Telegram, hosting). Lista y propósito.

### 12. Vulnerabilidades del stack
- Java/Spring Boot 3.2: CVEs relevantes, gestión de dependencias
  (OWASP Dependency-Check, Dependabot, Snyk).
- React 18: XSS, evitar `dangerouslySetInnerHTML`.
- PostgreSQL 15: hardening básico.

### 13. Plan de respuesta a incidentes
Procedimiento mínimo si se detecta brecha: contención, evaluación,
notificación (RGPD: 72h a autoridad), comunicación.

## Reglas de construcción

1. **No inventes amenazas que no aplican.** PadelPro es una app de gestión
   de club de pádel, no un banco. Calibra severidad.
2. **Toda decisión justificada.** "Usamos JWT" no vale; "Usamos JWT con
   RS256 porque X" sí.
3. **Coherencia con `docs/data-model.md`.** Si menciono "tabla de auditoría",
   debe existir o quedar declarada como pendiente.
4. **Coherencia con `docs/PROJECT.md`.** Mismos roles, mismo stack, mismas
   URLs de entorno.
5. **Verificable.** Todo control debe tener un "cómo se prueba" que
   `api-tester` pueda implementar.

## Entregable

Crea `docs/security-design.md` con la estructura anterior. Al final, sección
"Decisiones abiertas" con todo lo que requiera input mío o de negocio
(política exacta de retención, longitud de tokens, etc.).

Cuando termines, devuélveme un resumen de 15-20 líneas con: roles definidos,
decisiones clave (algoritmo JWT, lifetimes, 2FA sí/no, política de bloqueo),
y riesgos altos no mitigados aún. Espera mi OK antes de proponer commit.


----------------------------------------------------


Actúa como backend-architect. Tu tarea es generar `docs/openapi.yaml` desde
cero para el proyecto PadelPro.

## Paso 0 — Contexto obligatorio
Antes de escribir nada, lee en este orden:
1. `docs/PROJECT.md` — stack, URLs de entornos, convenciones.
2. `README.md` — especificación funcional.
3. `docs/data-model.md` — entidades y campos (base de los schemas).
4. `docs/security-design.md` — auth, roles, matriz RBAC, OWASP.
5. `backlog.md` — para identificar todos los endpoints implícitos en las US y Tickets.

Si falta alguno de los dos primeros docs técnicos, detente.

## Objetivo
Producir un contrato OpenAPI 3.1 completo y válido que sirva como:
- Fuente de verdad del contrato HTTP entre frontend y backend.
- Input para generación de clientes (`openapi-generator`).
- Input para `api-tester` (validación de shape y RBAC).
- Documentación interactiva (Swagger UI / Redoc).

## Especificación obligatoria

### Versión
OpenAPI 3.1.0.

### Info
- title, version (0.1.0 inicial), description, contact (cuenta `orquestadoria`),
  license si aplica.

### Servers
Tres servidores: DES, PRE, PRO. URLs extraídas de `docs/PROJECT.md`. Cada uno
con `description`.

### Security schemes
- `bearerAuth` (JWT, formato `bearer` con `bearerFormat: JWT`).
- Otros que defina `docs/security-design.md` (ej. API key para webhook
  Redsys si aplica).
- `security` global por defecto = `bearerAuth`, salvo endpoints públicos
  (login, register, webhook Redsys) que lo sobrescriben con `security: []`
  o con su propio esquema.

### Tags
Un tag por módulo funcional (Autenticación, Usuarios, Reservas, Pistas,
Pagos, Partidas, Notificaciones, etc.). Cada tag con `description`.

### Paths
Cobertura completa de:
- **Auth**: login, refresh, logout, register, forgot-password,
  reset-password, OTP Telegram (initiate, verify).
- **Usuarios**: CRUD, perfil propio (`/users/me`), cambio de contraseña,
  gestión de roles (solo ADMIN).
- **Pistas**: CRUD, disponibilidad por franja.
- **Reservas**: crear, listar (con filtros), cancelar, ver detalle.
- **Partidas / matches**: crear, unirse, abandonar, listar abiertas.
- **Pagos Redsys**: iniciar pago, callback/webhook, consultar estado.
- **Bot Telegram**: vincular cuenta, desvincular.
- **Cualquier otro recurso** que aparezca en el backlog.

Para cada path:
- `summary` corto y `description` con detalle.
- `operationId` en camelCase, único.
- `tags` (uno principal).
- `parameters` (path, query, header) con `schema`, `required`, `description`,
  `example`.
- `requestBody` con `content` y `schema` referenciado.
- `responses`: al menos `200/201`, `400`, `401`, `403`, `404`, `409` (si
  aplica), `422` (validación), `500`. Cada uno con su schema y al menos
  un `example`.
- `security` explícito si difiere del global.

### Components

#### Schemas
- Un schema por DTO de request y un schema por DTO de response. NO reutilices
  el mismo schema para entrada y salida (los campos generados como `id`,
  `createdAt` no van en request).
- Schema **`ApiResponse<T>`** genérico si `docs/PROJECT.md` declara envelope
  estándar. Si no, decide formato (envelope vs raw) y justifica en
  description.
- Schema **`ErrorResponse`** unificado: `code`, `message`, `details[]`,
  `timestamp`, `path`. Coherente con lo que valide `api-tester`.
- Schema **`PageResponse<T>`** para listados paginados: `content`,
  `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- Enums declarados como `enum` en el schema, con `description` por valor si
  no son obvios.
- Constraints en cada campo: `minLength`, `maxLength`, `pattern`, `minimum`,
  `maximum`, `format` (`email`, `uuid`, `date-time`, `uri`).
- `nullable: true` SOLO donde realmente aplica.
- `example` o `examples` en cada schema completo.

#### Parameters reutilizables
- `PageParam`, `SizeParam`, `SortParam` para paginación.
- `IdParam` (UUID) si lo declara `docs/data-model.md`.

#### Responses reutilizables
- `Unauthorized` (401), `Forbidden` (403), `NotFound` (404),
  `ValidationError` (400/422), `InternalError` (500).
  Cada path los referencia con `$ref` en vez de repetir.

#### Examples
- Examples nombrados para los casos más comunes, especialmente login,
  creación de reserva, error de validación.

### Webhooks (OpenAPI 3.1)
Si Redsys o Telegram envían webhooks al backend, modélalos en la sección
`webhooks` (no en `paths`).

## Reglas de construcción

1. **Coherencia total con `docs/data-model.md`.** Cada campo del schema debe
   existir en la entidad correspondiente o ser un campo derivado justificado.
2. **Coherencia total con `docs/security-design.md`.** Los `security` por
   endpoint deben reflejar la matriz RBAC. Cada endpoint protegido debe
   declarar `403` cuando el rol no aplica.
3. **`api-tester` debe poder validarlo.** Cada response debe tener schema y
   example. Cada error debe seguir el shape declarado.
4. **YAML válido y parseable.** Termina ejecutando un linter (Spectral
   o `swagger-cli validate`) y arregla todo lo que reporte.
5. **No inventes endpoints que no estén en el backlog.** Si descubres un
   endpoint necesario que no aparece en backlog, listalo aparte como
   "endpoints sugeridos para añadir al backlog", no lo incluyas.
6. **UUID vs integer**: usa lo que declare `docs/data-model.md`. No mezcles.
7. **Fechas**: `format: date-time` (ISO 8601 con zona) salvo justificación.

## Entregable

Crea `docs/openapi.yaml`. Tras crearlo:
1. Ejecuta validación con un linter de OpenAPI (`npx @stoplight/spectral-cli
   lint docs/openapi.yaml` o equivalente disponible).
2. Muéstrame la salida del linter.
3. Si hay errores, corrígelos y revalida.

Al final, devuélveme un resumen con:
- Número de endpoints documentados, agrupados por tag.
- Número de schemas definidos.
- Cobertura del backlog: qué US/Tickets tienen endpoint y cuáles no.
- Endpoints sugeridos no presentes en backlog (si los hay).
- Resultado del linter.

Espera mi OK antes de proponer commit.