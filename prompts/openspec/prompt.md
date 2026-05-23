# Misión

Genera la estructura inicial de **OpenSpec** para PadelPro:
- `openspec/config.yaml` — contexto y reglas del proyecto.
- `openspec/project.md` — descripción del producto y dominio (glosario,
  usuarios, capabilities) extraída del README.
- `openspec/AGENTS.md` — guía genérica para agentes LLM sobre cómo
  navegar el directorio openspec/.
- `openspec/specs/<capability>/spec.md` — un spec por cada capability
  funcional, con escenarios BDD.

Esto sustituye el típico documento `use-cases.md`: en OpenSpec, los
casos de uso viven dentro de cada spec de capability como **escenarios
Given/When/Then**.

# Entradas

Lee en este orden antes de generar nada:
1. `README.md` — especificación funcional y técnica completa de PadelPro.
2. `docs/PROJECT.md` — variables operativas, stack, mapeo con GitHub
   Projects, identidad del bot `orquestadoria`.
3. `docs/data-model.md` — entidades por capability. Si no existe,
   detente.
4. `docs/security-design.md` — RBAC, roles, OWASP, pasarela Redsys, bot
   Telegram, RGPD. Si no existe, detente.
5. `docs/openapi.yaml` — endpoints por capability. Si no existe,
   puedes generar la estructura inicial igualmente y dejar los
   endpoints marcados como "pendiente de OpenAPI".
6. `docs/TESTING-STRATEGY.md` si existe — para alinear flujos críticos.
7. `backlog.md` — Épicas y US para cruzar contra capabilities.

# Capabilities a generar para PadelPro

Una capability por módulo funcional. Propuesta inicial (14):

1. `auth-local` — registro, login, refresh, logout, recuperación de
   contraseña, hash de credenciales.
2. `auth-otp-telegram` — vinculación de cuenta con Telegram, OTP de
   segundo factor, comandos del bot.
3. `usuarios` — gestión de perfil, datos personales, RGPD básico.
4. `roles-permisos` — gestión administrativa de roles, matriz RBAC,
   asignación y revocación.
5. `clubes` — alta y configuración del club (datos, horario de
   apertura, política de cancelación, política de reservas).
6. `pistas` — CRUD de pistas, tipos (cristal, muro, indoor/outdoor),
   estado (activa/mantenimiento), horarios disponibles.
7. `reservas` — creación, cancelación, modificación, listado con
   filtros, conflicto de horario, política de cancelación.
8. `partidas` — creación de partida pública/privada, unirse, abandonar,
   listado de partidas abiertas, emparejamiento.
9. `pagos-redsys` — iniciación de pago, firma HMAC SHA-256, callback
   del webhook, idempotencia, reconciliación, reembolsos.
10. `disponibilidad-pistas` — cálculo de slots libres por pista y franja
    horaria, calendario consultable.
11. `notificaciones` — emails y mensajes Telegram (confirmación de
    reserva, recordatorio, cancelación, recibo de pago).
12. `auditoria` — registro de eventos sensibles (login, cambio de rol,
    pago, cancelación, acceso a datos de otro usuario), retención RGPD.
13. `exportaciones-rgpd` — derecho de acceso y portabilidad (export
    JSON/CSV de los datos del usuario), derecho al olvido
    (anonimización).
14. `administracion-club` — dashboard del manager (ocupación, ingresos,
    jugadores frecuentes, exportaciones de uso).

**Antes de generar nada, valida esta lista con el usuario**. Si una
capability no aplica al MVP, márcala como Fase 2. Si falta alguna que
se derive del backlog de PadelPro, propónla.

# Estructura del output

```
openspec/
├── AGENTS.md
├── config.yaml
├── project.md
└── specs/
    ├── auth-local/spec.md
    ├── auth-otp-telegram/spec.md
    ├── usuarios/spec.md
    ├── roles-permisos/spec.md
    ├── clubes/spec.md
    ├── pistas/spec.md
    ├── reservas/spec.md
    ├── partidas/spec.md
    ├── pagos-redsys/spec.md
    ├── disponibilidad-pistas/spec.md
    ├── notificaciones/spec.md
    ├── auditoria/spec.md
    ├── exportaciones-rgpd/spec.md
    └── administracion-club/spec.md
```

## `openspec/config.yaml`

```yaml
project:
  name: PadelPro
  description: Gestión integral de club de pádel (reservas, pagos, partidas, bot Telegram)
  owner: lcasadov
  language: es

stack:
  backend: Spring Boot 3.2 (Java 21, arquitectura hexagonal Maven multi-módulo)
  frontend: React 18 + Vite + TypeScript
  database: PostgreSQL 15
  migrations: Flyway
  orm: Spring Data JPA + Hibernate
  infra_local: Docker Compose
  ci: GitHub Actions
  payment_gateway: Redsys (HMAC SHA-256)
  bot: Telegram Bot API

conventions:
  api_base_path: /api/v1
  api_spec: docs/openapi.yaml
  data_model: docs/data-model.md
  security_design: docs/security-design.md
  testing_strategy: docs/TESTING-STRATEGY.md
  project_vars: docs/PROJECT.md

github:
  org: lcasadov
  repo: PadelPro
  project_owner: lcasadov
  project_manager: orquestadoria
  project_number: 1
  base_branch: develop
  release_branch: main

roles:
  - ADMIN              # superadmin de la plataforma
  - MANAGER_CLUB       # responsable del club (gestiona pistas, reservas, pagos)
  - JUGADOR            # usuario final que reserva y juega
  - INVITADO           # acceso público limitado (consultar disponibilidad)
# Si security-design.md define otros roles, sobrescribir esta lista.

phases:
  - id: fase-1
    name: MVP — reservas, pagos y bot básico
    status: active
  - id: fase-2
    name: Partidas públicas y emparejamiento
    status: planned
  - id: fase-3
    name: Multi-club y federaciones
    status: planned

capabilities:
  - auth-local
  - auth-otp-telegram
  - usuarios
  - roles-permisos
  - clubes
  - pistas
  - reservas
  - partidas
  - pagos-redsys
  - disponibilidad-pistas
  - notificaciones
  - auditoria
  - exportaciones-rgpd
  - administracion-club

business_rules:
  source: docs/security-design.md
  prefix: RN-
  # Si las RN-xx no están aún definidas en security-design.md, este
  # documento las introducirá por primera vez. Cada spec referencia
  # RN-xx por código.

mapping_to_github:
  capability_to_label: "area:<capability>"
  user_story_format: "US-NNN · <descripción>"
  ticket_format: "TICKET-NNN · <descripción>"
```

## `openspec/project.md`

Documento de descripción del producto y dominio. Estructura:

```markdown
# PadelPro — Descripción del producto

## Qué es
<2-3 párrafos extraídos del README.md>

## Usuarios y roles
| Rol | Quién es | Qué hace |
|---|---|---|
| ADMIN | ... | ... |
| MANAGER_CLUB | ... | ... |
| JUGADOR | ... | ... |
| INVITADO | ... | ... |

## Glosario del dominio
- **Pista**: superficie de juego, con tipo (cristal/muro) y modalidad
  (indoor/outdoor).
- **Reserva**: bloque de tiempo asignado a un jugador sobre una pista.
- **Partida pública**: reserva abierta a la que otros jugadores se
  pueden unir.
- **Franja horaria**: unidad de reserva (típicamente 60/90 minutos).
- **Club**: entidad organizativa con pistas, política de cancelación,
  horarios.
- **Política de cancelación**: reglas de reembolso según anticipación.
- **Vinculación Telegram**: enlace entre cuenta PadelPro y chat_id.
(extraer del README todos los términos del dominio padel)

## Capabilities
Lista de capabilities con resumen de 1-2 líneas y enlace relativo al
spec.

## Fases del producto
- Fase 1 (MVP): reservas, pagos Redsys, bot Telegram básico.
- Fase 2: partidas públicas y emparejamiento.
- Fase 3: multi-club, federaciones, ranking.

## Flujos críticos
- Reserva con pago Redsys end-to-end (incluye idempotencia y
  reconciliación de webhook).
- Vinculación Telegram + OTP de segundo factor.
- Derecho al olvido RGPD: anonimización + auditoría.
- Cancelación de reserva con cálculo de reembolso según política del
  club.
```

## `openspec/AGENTS.md`

Guía genérica para agentes LLM trabajando con OpenSpec en PadelPro:

```markdown
# AGENTS.md — Cómo trabajar con OpenSpec en PadelPro

Este documento explica a cualquier agente LLM cómo navegar el
directorio `openspec/`.

## Orden de lectura obligatorio
1. `openspec/config.yaml` — contexto del proyecto.
2. `openspec/project.md` — dominio y glosario.
3. `openspec/specs/<capability>/spec.md` — comportamiento esperado de
   la capability sobre la que vas a trabajar.

## Qué es una capability
Un módulo funcional de PadelPro con su contrato de comportamiento
expresado en escenarios Given/When/Then.

## Qué es un change
(Cuando exista `openspec/changes/`) Una propuesta de modificación
sobre uno o más specs. Estructura:
openspec/changes/<slug>/
  proposal.md
  design.md
  tasks.md
  specs/<capability>/spec.md  # delta a aplicar

## Reglas para agentes
- No modificar specs sin un change asociado.
- Cada cambio de comportamiento requiere actualizar el spec
  correspondiente.
- Las reglas de negocio se referencian por código RN-xx; nunca
  inventar reglas sin código.
- Antes de implementar, leer el spec completo de la capability
  afectada.
- Validar implementaciones contra los scenarios Given/When/Then del
  spec, no contra "lo que se entendió".

## Mapeo con GitHub Projects
Cada Issue de US/Ticket no trivial debe enlazar a su carpeta
`openspec/changes/<slug>/`. El agente `openspec-curator` (cuando
exista) se encarga de mantener esta correspondencia.

## Coherencia con docs/
- `docs/PROJECT.md` es ley operativa (stack, identidad bot, GitHub).
- `docs/data-model.md` es la fuente de entidades.
- `docs/security-design.md` es la fuente de RBAC y RN-xx.
- `docs/openapi.yaml` es la fuente de endpoints.
- OpenSpec describe COMPORTAMIENTO, no contrato HTTP ni esquema BD.
```

## Estructura de cada `spec.md`

Plantilla común para cada capability de PadelPro:

```markdown
# Capability: <nombre>

## Resumen
<2-3 líneas: qué hace esta capability en PadelPro>

## Fase
🟢 Fase 1 | 🔵 Fase 2 | 🟣 Fase 3 | combinación si aplica

## Reglas de negocio implicadas
- RN-XX (referencia a docs/security-design.md)
- RN-YY

## Entidades implicadas
- Reserva
- Pista
- Pago
(extraer de docs/data-model.md)

## Endpoints
- GET /api/v1/...
- POST /api/v1/...
(extraer de docs/openapi.yaml; si aún no existe, marcar "pendiente OpenAPI")

## Permisos
| Rol | Permisos |
|---|---|
| ADMIN | ... |
| MANAGER_CLUB | ... |
| JUGADOR | ... |
| INVITADO | ... |

## Requirements

### Requirement 1: <nombre>
**El sistema DEBE <comportamiento>.**

#### Scenario: <caso feliz>
- **GIVEN** <precondición>
- **AND** <precondición>
- **WHEN** <acción>
- **THEN** <resultado>
- **AND** <resultado>

#### Scenario: <caso de error>
- **GIVEN** ...
- **WHEN** ...
- **THEN** ...

### Requirement 2: <nombre>
...

## Casos límite (edge cases)
- <caso 1>
- <caso 2>

## Dependencias con otras capabilities
- Depende de `auth-local` para autenticación.
- Notifica a `notificaciones` al confirmar.
- Invoca `pagos-redsys` al cobrar.
```

# Cómo generar los escenarios BDD

Para cada capability, genera **al menos 3 requirements** con **al menos
2 scenarios cada uno** (uno feliz + uno de error). Cubre estos casos
explícitamente cuando sean relevantes:

- Camino feliz.
- Validación de campos (input inválido → 400/422).
- Autenticación (sin token → 401).
- Autorización (rol incorrecto → 403).
- Reglas de negocio violadas (devolver código RN-xx en el mensaje
  de error).
- Conflictos (409: doble reserva de la misma pista en la misma franja).
- Idempotencia (webhook Redsys duplicado, doble POST de reserva).
- RGPD (no exponer datos personales en errores, logs sin datos
  sensibles).
- Concurrencia (dos usuarios reservando la misma franja a la vez).

# Ejemplo: capability `reservas` (PadelPro)

```markdown
# Capability: reservas

## Resumen
Gestión de reservas de pista de pádel por jugadores. Incluye creación,
cancelación, modificación, listado con filtros, y aplicación de la
política de cancelación del club.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- RN-01 (franja mínima y máxima de duración de reserva)
- RN-02 (anticipación máxima en días para reservar)
- RN-03 (un jugador no puede tener dos reservas activas en la misma
  franja)
- RN-04 (política de cancelación del club: plazo y % de reembolso)
- RN-05 (notificación de confirmación AFTER_COMMIT)
- RN-06 (idempotencia en POST de reserva)

## Entidades implicadas
- Reserva, Pista, Usuario, Pago, Club

## Endpoints
- POST /api/v1/reservas
- GET /api/v1/reservas/mias
- GET /api/v1/reservas/{id}
- DELETE /api/v1/reservas/{id}
- PATCH /api/v1/reservas/{id}

## Permisos
| Rol | Permisos |
|---|---|
| JUGADOR | Crear/cancelar/ver propias, modificar propias dentro de plazo |
| MANAGER_CLUB | Ver todas las del club, cancelar cualquiera del club |
| ADMIN | Ver y gestionar todas |
| INVITADO | Sin permisos sobre reservas |

## Requirements

### Requirement 1: Crear reserva en franja libre
**El sistema DEBE permitir a un jugador autenticado crear una reserva
para una pista en una franja libre, dentro de la ventana de
anticipación del club, garantizando unicidad por pista-franja.**

#### Scenario: Jugador reserva pista libre para mañana
- GIVEN un jugador autenticado
- AND una pista activa con la franja `mañana 18:00-19:30` libre
- WHEN crea una reserva con `pistaId = P1`, `inicio = mañana 18:00`,
  `duracion = 90min`
- THEN el sistema responde 201 con `estado = PENDIENTE_PAGO`
- AND inicia el flujo de pago Redsys
- AND la fila queda en `reservas` con `pago_id = NULL` hasta confirmación

#### Scenario: Jugador intenta reservar franja ocupada (RN-03)
- GIVEN un jugador autenticado
- AND la pista `P1` ya tiene una reserva CONFIRMADA en
  `mañana 18:00-19:30`
- WHEN intenta crear una reserva en esa misma franja
- THEN el sistema responde 409 con `error = "FRANJA_OCUPADA"`
- AND el mensaje referencia RN-03

#### Scenario: Jugador reserva fuera de la ventana (RN-02)
- GIVEN un jugador autenticado
- AND el club tiene `anticipacion_maxima_dias = 14`
- WHEN crea una reserva con `inicio = hoy + 15 días`
- THEN el sistema responde 422 con `error = "FUERA_VENTANA"`
- AND el mensaje referencia RN-02

#### Scenario: Sin autenticación
- GIVEN una petición sin token
- WHEN POST /api/v1/reservas
- THEN el sistema responde 401

### Requirement 2: Cancelar reserva aplicando política del club
**El sistema DEBE permitir al jugador cancelar su reserva, aplicando
el porcentaje de reembolso según el plazo definido por el club.**

#### Scenario: Cancelación dentro de plazo (RN-04)
- GIVEN una reserva CONFIRMADA del jugador para `mañana 18:00`
- AND el club tiene `politica_cancelacion: 24h = 100% reembolso`
- WHEN el jugador cancela hoy (>24h antes)
- THEN el sistema responde 200
- AND la reserva queda en estado CANCELADA
- AND se genera reembolso del 100% vía Redsys
- AND se notifica al jugador por email

#### Scenario: Cancelación fuera de plazo (RN-04)
- GIVEN una reserva CONFIRMADA del jugador para `dentro de 2 horas`
- AND el club tiene `politica_cancelacion: 24h = 100%, <24h = 0%`
- WHEN el jugador cancela
- THEN el sistema responde 200
- AND la reserva queda en estado CANCELADA_SIN_REEMBOLSO
- AND no se genera reembolso
- AND el mensaje informa RN-04

### Requirement 3: Idempotencia en creación
**El sistema DEBE garantizar que dos POST con la misma
`Idempotency-Key` no creen dos reservas.**

#### Scenario: POST duplicado con misma key
- GIVEN un jugador autenticado
- AND ya envió POST con `Idempotency-Key: abc-123` que creó la
  reserva `R1`
- WHEN envía un segundo POST idéntico con la misma key
- THEN el sistema responde 200 con la reserva `R1` existente
- AND no se crea una segunda reserva

#### Scenario: POST con key distinta a misma franja
- GIVEN un jugador con reserva `R1` creada
- WHEN envía un nuevo POST sin `Idempotency-Key` (o con key distinta)
  para la misma franja
- THEN el sistema responde 409 (FRANJA_OCUPADA, RN-03)

## Casos límite
- Dos jugadores intentan reservar la misma franja en paralelo: el
  segundo recibe 409 (control de concurrencia con UNIQUE constraint
  `pista_id + inicio`).
- Jugador cancela reserva con pago en estado PENDIENTE (sin callback
  Redsys recibido): se cancela la reserva y se aborta el flujo de pago.
- Jugador modifica reserva (PATCH) para mover de franja: se valida
  como cancelación + creación atómica.
- Pista pasa a estado MANTENIMIENTO con reservas activas: las
  reservas existentes se mantienen pero no se permiten nuevas.

## Dependencias
- Requiere `auth-local` autenticado.
- Llama a `disponibilidad-pistas` al validar franja libre.
- Invoca `pagos-redsys` al confirmar.
- Dispara `notificaciones` en creación, cancelación y modificación.
- Audita en `auditoria` cada cambio de estado.
```

# Restricciones de generación

- Cada capability con al menos **3 requirements** y **6 scenarios**
  totales mínimo (más en capabilities críticas de PadelPro como
  `reservas`, `pagos-redsys`, `auth-local`).
- Cada scenario en formato Given/When/Then estricto (no narrativo).
- Referencia explícita a códigos RN-xx donde aplique. Si las RN-xx aún
  no están en `security-design.md`, **decláralas en este ejercicio**
  con códigos consistentes y márcalas para que se trasladen a
  `security-design.md` después.
- No copies código ni DDL. Referencia `data-model.md` y `openapi.yaml`.
- Idioma español (PadelPro es proyecto en español).
- Si una capability es pequeña (ej. `notificaciones`), igual necesita
  3 requirements: envío exitoso, fallo SMTP/Telegram, reintento con
  backoff.
- Coherencia obligatoria con `docs/PROJECT.md`, `docs/data-model.md` y
  `docs/security-design.md`. Si detectas inconsistencia, **detente y
  reporta** antes de generar.
- Capabilities críticas que merecen especial cuidado:
  - `auth-local` y `auth-otp-telegram`: cubrir OWASP API2 (broken
    authentication).
  - `pagos-redsys`: idempotencia, replay, reconciliación, firma HMAC.
  - `auditoria` y `exportaciones-rgpd`: cumplimiento RGPD verificable.

# Salida esperada

Genera **17 archivos**:
- `openspec/config.yaml`
- `openspec/project.md`
- `openspec/AGENTS.md`
- 14 archivos `openspec/specs/<capability>/spec.md`

Si no caben en una respuesta, divide así:
- **Turno 1**: `config.yaml` + `project.md` + `AGENTS.md` + las 5
  capabilities fundacionales (`auth-local`, `auth-otp-telegram`,
  `usuarios`, `roles-permisos`, `clubes`).
- **Turno 2**: las 5 capabilities de operación (`pistas`, `reservas`,
  `partidas`, `pagos-redsys`, `disponibilidad-pistas`).
- **Turno 3**: las 4 capabilities transversales (`notificaciones`,
  `auditoria`, `exportaciones-rgpd`, `administracion-club`).

Antes de empezar, devuélveme:
1. Confirmación de la lista de 14 capabilities (añadir, quitar,
   dividir, fusionar).
2. Confirmación de roles (`ADMIN`, `MANAGER_CLUB`, `JUGADOR`,
   `INVITADO`).
3. Lista preliminar de RN-xx que vas a usar, con código y enunciado de
   una línea cada una.

Espera mi OK a estos tres puntos antes de generar los archivos.

=== FIN DEL PROMPT ===

Eres el agente `frontend-engineer` del proyecto PadelPro. Tu misión en esta sesión es crear el sistema de documentación UX del proyecto en `docs/ux/`, usando como entrada los dos archivos de mockups en alta fidelidad que ya están en el repo (`tmp/padelpro-mockups-tanda1.html` y `tmp/padelpro-mockups-tanda2.html`).

Antes de tocar nada, lee `docs/PROJECT.md` para confirmar la identidad ejecutora, las ramas base y la convención de commits del proyecto. Verifica con `gh auth status` que estás actuando como `orquestadoria` antes de cualquier operación de escritura remota; si no, detente y avisa.

A continuación lee `README.md`, `backlog.md` y `openspec/project.md` si existe. Necesitas el léxico de dominio y el mapeo de capabilities para asignar cada pantalla a la suya. Después lee los dos HTML de mockups completos. Contienen 24 pantallas mobile y desktop con notas de diseño embebidas; esas notas te dicen qué capability, endpoints y permisos cubre cada pantalla.

Tu entregable es una rama nueva `feat/docs-ux-system` desde `develop` con esta estructura creada:

```
docs/ux/
  README.md
  flujos.md
  components.md
  design-tokens.md
  mockups/
    index.html
    01-login.html  ...  24-eliminar-cuenta.html
    _shared/styles.css
    _shared/fonts.html
```

Empieza partiendo los dos HTML en 24 archivos independientes numerados del 01 al 24 según el orden en que aparecen (las 6 de la tanda 1 primero, las 18 de la tanda 2 después). Extrae a `_shared/styles.css` todo el CSS común (variables `:root`, clases `.p-screen`, `.p-btn`, `.phone`, `.desktop`, todos los componentes reutilizables). Cada HTML individual queda con un `<link>` al CSS común, otro a Google Fonts cargando Bricolage Grotesque + Geist + Geist Mono, el HTML de UNA pantalla con su device frame correspondiente, y las notas de diseño convertidas en un `<aside>` lateral. No deben romperse visualmente respecto al original; ábrelas y compruébalo antes de seguir.

Crea `mockups/index.html` como overview en grid de las 24 pantallas. Thumbnails clicables que abren cada HTML individual en pestaña nueva. Reutiliza los mismos tokens visuales.

Ahora escribe `docs/ux/README.md`. Empieza con un párrafo de visión general del sistema visual (Bricolage Grotesque + Geist + Geist Mono, paleta crema/tinta/olive/lima, estética editorial deportiva, mobile-first jugador / desktop admin). Sigue con una sección "Cómo usar esta documentación" que enumere los archivos del directorio. Después un **índice de las 24 pantallas** donde cada una tenga un bloque con este formato exacto:

```markdown
### NN · Nombre

- **Archivo:** [`mockups/NN-slug.html`](./mockups/NN-slug.html)
- **Dispositivo:** Mobile (390×844) o Desktop (1100×720)
- **Capability OpenSpec:** `capability-slug`
- **Endpoints:** `METHOD /ruta`
- **Permisos:** rol o público
- **Historia de usuario:** US-XXX-NN si la encuentras en backlog.md, si no `—`
- **Estados representados:** vacío / con datos / error / cargando (los que apliquen)
- **Conecta con:** → NN Otra pantalla (condición), → NN Otra (otra condición)
```

Cierra el README con una tabla resumen de 24 filas con columnas `#`, `Pantalla`, `Dispositivo`, `Capability`, `Permisos`. Esta tabla es crítica: otro agente la leerá después para hacer cruces, así que asegúrate de que la columna `Capability` use el mismo slug que aparece en `openspec/specs/`.

Escribe `docs/ux/flujos.md` con al menos 6 diagramas Mermaid: onboarding (splash → registro/login → home + recuperar/reset), reserva de pista (home → buscar → detalle → checkout → pago OK → mis reservas → detalle reserva), partida pública (home → buscar/crear partida → unirse → checkout → pago OK), vinculación Telegram (perfil → vincular → OTP → perfil vinculado), RGPD (perfil → mis datos → eliminar cuenta), y admin club (login admin → dashboard → calendario / pistas / reservas / pagos). Usa `graph TD` o `graph LR` según legibilidad. Etiqueta nodos como `S01[01 · Login]` y transiciones con la condición (`S01 -->|login OK| S02`). Define `classDef error` y `classDef success` al inicio del archivo. Después de cada diagrama, máximo 4 líneas explicando los casos límite (qué pasa si falla el pago, si caduca el OTP, etc).

Escribe `docs/ux/components.md`. Para cada componente reutilizable que identifiques en los mockups, una sección con: nombre, variantes, cuándo usarlo, en qué pantallas aparece (lista de números), el CSS exacto extraído de `_shared/styles.css`, y una nota de accesibilidad (contraste WCAG). Documenta como mínimo: `p-btn` y variantes, `p-input`, `p-card`, `p-tabbar`, `p-eyebrow`, `p-h1` y `p-h2`, `chip`, `slot`, `player-chip`, `info-pill`, `home-next`, `kpi` y `kpi.hero`, `timeline`, `cal-event`, `table-head` y `table-row`, `otp-box`, `card-preview`, `success-ico`, `delete-warn`. Cierra con una tabla de uso cruzado de dos columnas: componente / pantallas donde aparece.

Escribe `docs/ux/design-tokens.md` con todos los tokens CSS extraídos de `_shared/styles.css` en formato de tabla. Paleta con sus 16 variables (`--bg-cream`, `--ink`, `--olive`, `--lime`, etc.) explicando el uso de cada una. Tipografía con las 3 familias y pesos cargados. Escala tipográfica con los tamaños de hero, h1, h2, body, eyebrow. Espaciado con la escala 4/8/12/16/22/28/36. Border radius con los valores 6-8 / 10-14 / 16-20 / 24 / 38-48 / 100. Sombras con las 2-3 usadas en device frames y focus states. Cualquier desarrollador frontend del proyecto debe poder usar este archivo como referencia única.

Antes de cerrar, valida lo siguiente y no abras PR hasta que pasen todos:

- Los 24 HTML individuales se abren sin errores en consola.
- `mockups/index.html` muestra los 24 thumbnails y todos los enlaces funcionan.
- Los diagramas Mermaid de `flujos.md` se renderizan correctamente (pruébalos contra `https://mermaid.live` o con el plugin local que use el equipo).
- Todos los enlaces relativos del README resuelven a archivos existentes.
- La tabla resumen del README tiene exactamente 24 filas.
- `components.md` documenta al menos los 19 componentes listados arriba.
- Todos los tokens de `design-tokens.md` están en `_shared/styles.css`.

Haz commits granulares con mensajes en convención del proyecto: `docs(ux): extraer CSS común de mockups`, `docs(ux): añadir 24 mockups individuales`, `docs(ux): índice maestro y tabla de pantallas`, `docs(ux): diagramas Mermaid de flujos`, `docs(ux): catálogo de componentes`, `docs(ux): design tokens`. Después abre PR contra `develop` titulada `docs(ux): sistema de diseño y mockups en alta fidelidad`, añade a `lcasadov` como reviewer, mueve el issue del Project v2 a `In Review`, y reporta en el comentario del PR un resumen de qué has creado y cualquier decisión que hayas tenido que tomar por tu cuenta.

Si en algún punto te encuentras con que un archivo de los que pides leer no existe, o que el mapeo capability/pantalla es ambiguo, o que el CSS no es extraíble limpiamente, no improvises: para, escribe un comentario en el issue describiendo el problema concreto, etiqueta a `lcasadov`, y termina con `status: blocked`. Es mejor pausar que entregar algo inventado.


-------------------------


Eres el agente curador de specs OpenSpec del proyecto PadelPro (asume el rol de `openspec-curator` si existe, o el de `backend-architect` como fallback). Tu misión en esta sesión es vincular los specs de `openspec/specs/` con la documentación UX que ya está creada en `docs/ux/`. El resultado es que cada spec apuntará a las pantallas que lo ilustran, sin embeber HTML ni SVG dentro del spec, manteniendo separadas la especificación (qué construir) y la UX (cómo se ve).

Antes de empezar, lee `docs/PROJECT.md` y confirma identidad ejecutora con `gh auth status`; debes ser `orquestadoria`. Si no, detente. Después lee `openspec/AGENTS.md` y `openspec/project.md` para conocer la convención de specs del proyecto, y comprueba que existe `docs/ux/README.md`. Si no existe, o si no contiene la tabla resumen con la columna `Capability OpenSpec`, el Prompt A no se ha ejecutado correctamente. Para, abre issue avisando a `lcasadov`, y termina con `status: blocked`.

Tu fuente de verdad es la tabla resumen de `docs/ux/README.md`. De ahí extraes el mapeo `{capability: [pantallas]}` que vas a aplicar. Lista también los directorios bajo `openspec/specs/` para conocer qué capabilities existen.

Cruza ambas listas y detecta cuatro situaciones:

1. Capability con pantallas asignadas en el README → recibirá sección detallada.
2. Capability sin pantallas (transversal o puramente backend, como `clubes` o `roles-permisos`) → recibirá sección breve reconociendo esa naturaleza.
3. Pantalla en el README cuya capability no tiene carpeta en `openspec/specs/` → BLOCKER. Para y avisa.
4. Carpeta en `openspec/specs/` que no aparece en ninguna capability del README → la procesas como caso 2.

Si te encuentras con un blocker del caso 3, no inventes una capability nueva ni la asignes a otra "parecida". Para.

Por cada `openspec/specs/<capability>/spec.md`, vas a hacer una operación quirúrgica: añadir una sección al final llamada `## Mockups asociados`, sin tocar nada más. Reglas absolutas que no puedes romper:

- No modifiques `## Requirements` ni `## Scenarios` ni ninguna otra sección preexistente.
- Si ya hay una sección `## Mockups asociados` de una ejecución anterior, la sustituyes íntegra. No acumules ni añadas debajo.
- Nunca embebas SVG, HTML, base64 ni capturas de pantalla. Solo enlaces relativos.
- La sección va siempre al final del archivo.

Para una capability **con pantallas**, el formato exacto de la sección es:

```markdown
## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 01 | Login | Mobile | público | [`01-login.html`](../../../docs/ux/mockups/01-login.html) |
| ... |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo de onboarding** — splash → registro/login → home (incluye recuperación de contraseña).

### Notas de UX

> - Regla 1 derivada de los scenarios del spec.
> - Regla 2 derivada de los scenarios del spec.
```

Las "Notas de UX" no las inventas. Las generas leyendo los `## Scenarios` del propio spec y extrayendo reglas que tienen impacto visible en la UI: mensajes de error con restricciones de información (anti-enumeración, anti-timing-attack), reglas de privacidad y consentimiento RGPD, restricciones temporales visibles (caducidad de OTP, ventana de cancelación de reservas), validaciones de formulario (política de contraseñas, longitud mínima). Si el spec no contiene ningún scenario con impacto UX, omite la subsección "Notas de UX" entera.

Para una capability **sin pantallas** (transversal o backend), el formato es más corto:

```markdown
## Mockups asociados

Esta capability es **transversal o puramente backend**. No tiene pantallas de usuario directas en el sistema actual.

Está implícita en los siguientes mockups donde aparece de forma indirecta:

- (lista de pantallas donde el concepto aparece sin ser protagonista, por ejemplo `clubes` aparece en el sidebar admin con el pill "Club Madrid Este")

Si se evoluciona esta capability hacia una UI dedicada (por ejemplo gestor multi-club, pantalla de gestión de roles), añadir el flujo correspondiente en [`docs/ux/flujos.md`](../../../docs/ux/flujos.md) antes de generar mockups.
```

Cuando termines todos los specs, actualiza `docs/ux/README.md` añadiendo al final una sección nueva llamada `## Cruce con OpenSpec`. Es una tabla inversa que sirve de espejo. Debe contener una fila por capability presente en `openspec/specs/`, con columnas `Capability`, `Spec` (enlace al spec) y `Pantallas` (lista de números, o `—` si no tiene). Esta tabla es lo que permite a otro agente verificar la consistencia en el futuro.

Antes de hacer commit, ejecuta esta verificación de enlaces con bash:

```bash
grep -rhoE '\.\./\.\./\.\./docs/ux/[a-z0-9/_-]+\.(html|md)' openspec/specs/ | sort -u | while read link; do
  target="$(echo "$link" | sed 's|^\.\./\.\./\.\./|/|')"
  [ -f ".$target" ] || echo "BROKEN: $link"
done
```

Si la salida tiene cualquier línea `BROKEN:`, no hagas commit. Reporta los enlaces rotos al humano y termina con `status: blocked`. La causa habitual es que el slug de la pantalla en el README no coincide con el nombre real del archivo en `docs/ux/mockups/`; en ese caso es responsabilidad del Prompt A, no la corrijas tú.

Diff esperado del PR: solo deben aparecer modificaciones en `openspec/specs/*/spec.md` y en `docs/ux/README.md`. Si ves cualquier otro archivo cambiado, has tocado algo que no debías; revisa y limpia.

Haz commits granulares con la convención del proyecto: `docs(openspec): añadir sección Mockups asociados a cada spec` y `docs(ux): añadir tabla de cruce con OpenSpec en README`. Después abre PR contra `develop` titulada `docs(openspec+ux): vincular specs con mockups UX`. En la descripción del PR, incluye la tabla completa de cruce capability → pantallas como resumen verificable de un vistazo. Añade a `lcasadov` como reviewer y mueve el issue del Project v2 a `In Review`.

Si en algún momento detectas que el mapeo capability/pantalla no es resoluble (capability sin carpeta, pantalla huérfana, README incompleto), no improvises. Para, comenta el bloqueo concreto en el issue, etiqueta a `lcasadov`, y devuelve `status: blocked`. Prefiero parar a especular.

-----------------------------------------

Eres el agente curador de specs OpenSpec del proyecto PadelPro (asume el rol de `openspec-curator` si existe en `.claude/agents/`, o el de `backend-architect` como fallback). Tu misión en esta sesión es crear el primer change del proyecto: `bootstrap-mvp`. Este change establece el andamiaje OpenSpec del producto y declara la primera capability mínima viable, `auth-local`, suficiente para que un usuario pueda registrarse e iniciar sesión con email y contraseña. No vas a escribir código de producción. Vas a generar las specs y la propuesta de change en formato OpenSpec, listas para ser ejecutadas más adelante por agentes de backend y frontend.

Antes de tocar nada, lee `docs/PROJECT.md` para confirmar identidad ejecutora, ramas base y convención de commits. Verifica con `gh auth status` que estás actuando como `orquestadoria` antes de cualquier operación de escritura remota. Si no, detente y avisa.

Lee después, en este orden: `README.md` (descripción funcional y técnica), `backlog.md` (épicas y user stories, busca específicamente EP-01 o equivalente de autenticación), `docs/ux/README.md` y `docs/ux/flujos.md` (la UX de onboarding ya está documentada, vas a referenciarla desde el change), y la estructura actual de `openspec/` para confirmar que no existe todavía un change `bootstrap-mvp`. Si existe, para y avisa: no quieres pisar trabajo previo.

Si `openspec/project.md` o `openspec/AGENTS.md` no existen aún, créalos como parte del andamiaje. Si ya existen, los respetas y los referencias desde el change.

Tu entregable es una rama nueva `feat/openspec-bootstrap-mvp` desde `develop` con esta estructura creada bajo `openspec/`:

```
openspec/
  project.md                       (si no existe ya)
  AGENTS.md                        (si no existe ya)
  changes/
    bootstrap-mvp/
      proposal.md
      design.md
      tasks.md
      specs/
        auth-local/
          spec.md
```

Empieza por `openspec/project.md` si no existe. Es un documento corto, declarativo, que describe el producto en términos de dominio. Incluye: visión en una frase, audiencias (jugador, manager de club, admin), glosario de términos del dominio (club, pista, reserva, partida, slot, OTP, etc.), roles del sistema (ADMIN, MANAGER_CLUB, JUGADOR, INVITADO) con una línea cada uno, y stack tecnológico de referencia (Java 21 + Spring Boot 3.2, hexagonal, PostgreSQL 15 + Flyway, React 18 + Vite + TypeScript, Redsys, bot Telegram). Este documento es la "constitución" del proyecto; los siguientes changes lo respetarán.

Sigue por `openspec/AGENTS.md` si no existe. Es la guía para que cualquier agente LLM (incluido tú en futuras sesiones) sepa cómo trabajar con OpenSpec en este repo. Explica: qué es un change (propuesta atómica de cambio con proposal/design/tasks/specs), qué es una capability (área coherente de funcionalidad con un spec.md propio), la convención de slugs (kebab-case en español: `auth-local`, `pagos-redsys`, `disponibilidad-pistas`), el flujo de vida de un change (draft → proposed → approved → in-progress → done → archived), y la regla crítica de que los changes solo modifican specs cuando entran en estado `done` (los specs en `openspec/specs/` reflejan el estado actual del producto; los changes en `openspec/changes/` son la propuesta de cambio). Cierra con un ejemplo: "para añadir un nuevo flujo, crea `openspec/changes/<slug>/` con proposal.md, design.md, tasks.md y specs/<capability>/spec.md".

Ahora el change en sí. Crea `openspec/changes/bootstrap-mvp/proposal.md` con esta estructura: título "Bootstrap MVP", contexto (por qué este change existe: el proyecto necesita un esqueleto OpenSpec funcional y una primera capability autenticación mínima para que el resto de capabilities puedan apoyarse en un usuario identificado), alcance dentro (qué declara: project.md, AGENTS.md, capability auth-local mínima con registro y login por email+contraseña, JWT como mecanismo de sesión), alcance fuera (qué NO declara: OTP de Telegram, recuperación de contraseña, perfiles, roles avanzados, todo eso son changes futuros), criterios de aceptación (la documentación OpenSpec queda creada y validable; el spec de auth-local declara los requirements mínimos con scenarios Given/When/Then; existe trazabilidad a los mockups 01 Login, 07 Splash y 08 Registro de `docs/ux/`), riesgos (decisiones de seguridad como algoritmo de hashing, expiración de JWT, formato de errores, que se cierran en design.md), e impacto en otros changes (todos los siguientes dependen de este: auth-otp-telegram extenderá auth-local, usuarios consumirá el concepto de identidad creado aquí, etc.).

Crea `openspec/changes/bootstrap-mvp/design.md`. Aquí cierras las decisiones técnicas de la capability auth-local. Estructura: decisión 1, algoritmo de hashing de contraseñas (recomienda BCrypt con factor 12 o Argon2id si el equipo lo prefiere, justifica la elección por OWASP ASVS), decisión 2, formato del token de sesión (JWT firmado con qué algoritmo, RS256 vs HS256, qué claims lleva, qué duración tiene access y refresh), decisión 3, formato de respuesta de error en endpoints de auth (debe ser consistente con la regla anti-enumeración: nunca revelar si el email existe; un único mensaje genérico "Credenciales inválidas"), decisión 4, política de contraseñas mínima (8 caracteres, una mayúscula, un número, un símbolo, alineada con la pantalla 10 Reset password de `docs/ux/`), decisión 5, qué se persiste en base de datos (tabla `users` con columnas mínimas: id, email único, password_hash, created_at, updated_at, last_login_at), decisión 6, rate limiting de endpoints públicos (`/auth/login` y `/auth/register` deben tener throttle, propón un valor razonable como 5 intentos por minuto por IP). Cierra el documento con una sección "Decisiones aplazadas" que liste lo que NO decide este change y se cerrará en changes posteriores: recuperación de contraseña, verificación de email, 2FA por Telegram, sesiones simultáneas múltiples, expiración por inactividad.

Crea `openspec/changes/bootstrap-mvp/tasks.md`. Es la lista de subtareas operativas que ejecutarán los agentes downstream cuando este change pase a `in-progress`. No es un timeline; es un checklist. Estructúralo en tres bloques: bloque DOCS (crear/actualizar project.md, AGENTS.md, validar enlaces), bloque BACKEND (modelo `User` en dominio, repositorio JPA, servicio de registro, servicio de login, generador y validador de JWT, controlador REST con endpoints `POST /api/v1/auth/register` y `POST /api/v1/auth/login`, migración Flyway V1 con tabla `users`, tests unitarios para servicio de hash, tests de integración con Testcontainers para los endpoints), bloque FRONTEND (formulario de login conectado al endpoint, formulario de registro conectado al endpoint, persistencia de JWT en almacenamiento seguro, guard de rutas privadas, página de splash con CTAs de login y registro alineada con mockup 07). Cada tarea con un checkbox markdown `- [ ]` y un identificador `T-001`, `T-002`, etc., para que sean referenciables en commits y PRs futuras.

Y ahora la pieza central: el spec de la capability. Crea `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md`. Este es el contrato de la capability tal como quedará cuando el change esté `done`. Estructura: encabezado "# Capability: auth-local", una frase de propósito ("Permite a un usuario crear una cuenta y autenticarse con email y contraseña, obteniendo un token JWT para llamadas posteriores"), una sección `## Roles que la consumen` listando JUGADOR e INVITADO (los demás roles vienen autenticados por otra vía o por otra capability futura), y una sección `## Requirements` con al menos cuatro requirements numerados.

Cada requirement va con título descriptivo y luego una subsección `### Scenarios` con dos o más escenarios en formato Given/When/Then. Los requirements obligatorios son: R-1 registro de usuario con email único (scenarios: registro válido, registro con email ya existente, registro con contraseña que no cumple política, registro sin aceptar términos), R-2 login con credenciales válidas (scenarios: login válido devuelve JWT, login con email inexistente devuelve mismo error que credenciales incorrectas para evitar enumeración, login con contraseña incorrecta), R-3 emisión y validación de JWT (scenarios: token recién emitido es válido, token caducado se rechaza, token con firma manipulada se rechaza), R-4 rate limiting de endpoints públicos (scenarios: por debajo del umbral se aceptan peticiones, al superar el umbral se devuelve 429 con cabecera Retry-After). Añade un quinto requirement si lo ves necesario para auditoría: R-5 cada intento de login (exitoso o fallido) genera una entrada de auditoría con timestamp, IP y resultado, sin exponer la contraseña.

Cierra el spec con una sección `## Mockups asociados` que apunte a las pantallas relevantes en `docs/ux/`. Para este change deben ser las pantallas 01 Login, 07 Splash y 08 Registro. Usa enlaces relativos desde `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md` hacia `docs/ux/mockups/NN-slug.html` y `docs/ux/README.md`. Sigue el formato que ya quedó establecido por el Prompt B en otros specs: tabla de pantallas, flujos relacionados (apunta al "Flujo de onboarding" de `docs/ux/flujos.md`), y notas de UX derivadas de los scenarios anti-enumeración y política de contraseñas.

Antes de hacer commit, valida lo siguiente:

- `openspec/project.md` y `openspec/AGENTS.md` existen y están coherentes con el resto del proyecto.
- `openspec/changes/bootstrap-mvp/` contiene los cuatro archivos esperados: proposal.md, design.md, tasks.md, specs/auth-local/spec.md.
- Todos los enlaces relativos en el spec hacia `docs/ux/` resuelven a archivos existentes (verifícalo con `ls`).
- Los scenarios de cada requirement están escritos en formato Given/When/Then sin ambigüedad; un agente de backend debería poder convertirlos en tests sin tener que adivinar.
- Las decisiones de design.md no contradicen a las pantallas referenciadas (por ejemplo, si la pantalla 10 Reset password muestra una política de contraseñas, esa misma política debe aparecer en design.md, aunque la pantalla en sí pertenezca a un change futuro de recuperación).
- No hay ningún archivo fuera de `openspec/` modificado, salvo `docs/ux/README.md` si decides añadir una nota cruzada (opcional).

Haz commits granulares con la convención del proyecto: `docs(openspec): añadir project.md y AGENTS.md`, `feat(openspec): change bootstrap-mvp · proposal y design`, `feat(openspec): change bootstrap-mvp · tasks operativas`, `feat(openspec): capability auth-local · requirements y scenarios`. Después abre PR contra `develop` titulada `feat(openspec): change bootstrap-mvp · andamiaje + auth-local`. En la descripción del PR incluye: el contenido completo de proposal.md como introducción, la lista de tareas T-001…T-NN extraída de tasks.md como checklist verificable, y la tabla de scenarios del spec de auth-local como resumen de qué queda contractualmente acordado. Añade a `lcasadov` como reviewer y mueve el issue del Project v2 a `In Review`.

Si en algún punto detectas que algún archivo de los que pides leer no existe (especialmente `docs/ux/README.md`, `docs/ux/flujos.md` o las pantallas 01/07/08 en `docs/ux/mockups/`), no improvises: para, escribe un comentario en el issue explicando qué falta y por qué bloquea, etiqueta a `lcasadov`, y termina con `status: blocked`. La capability auth-local depende de que la UX ya esté documentada para poder referenciarla correctamente; sin esa base, el spec quedaría huérfano.

Si detectas ambigüedad en alguna decisión de design.md (por ejemplo, no encuentras criterio claro en el proyecto entre BCrypt y Argon2id, o entre RS256 y HS256), no decidas a ciegas: deja la decisión documentada como "propuesta inicial" con justificación, etiqueta el commit como `[DECISION-PENDING]`, y deja explícito en el PR que esos puntos necesitan validación humana antes de pasar el change a `approved`.