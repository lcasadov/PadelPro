# PadelPro — Variables operativas del proyecto

> **Fuente de verdad** que TODOS los agentes leen al inicio de cualquier operación. Si una variable no está aquí, se cancela la operación.
> Última actualización: 2026-05-02 — Project v2 reubicado bajo `lcasadov` (owner) con `orquestadoria` como Admin; el Project temporal bajo `orquestadoria` ha sido eliminado.

---

## Repositorio (GitHub)

| Variable | Valor |
|---|---|
| `GITHUB_ORG` | `lcasadov` |
| `GITHUB_REPO` | `PadelPro` |
| `GITHUB_REMOTE` | `https://github.com/lcasadov/PadelPro.git` |
| `REPO_ROOT` | `C:/proyectos/PadelPro` |
| `BASE_BRANCH` | `develop` |
| `RELEASE_BRANCH` | `main` |

## Project v2 (gestión de trabajo)

| Variable | Valor |
|---|---|
| `GITHUB_PROJECT_OWNER` | `lcasadov` (owner; el dueño del repo es también dueño del Project) |
| `GITHUB_PROJECT_MANAGER` | `orquestadoria` (Admin collaborator; ejecuta todas las operaciones de gestión) |
| `GITHUB_PROJECT_NUMBER` | `1` |
| `GITHUB_PROJECT_ID` (GraphQL node ID) | `PVT_kwHOAGwvnc4BWZD6` |
| `GITHUB_PROJECT_URL` | https://github.com/users/lcasadov/projects/1 |

> **Modelo de propiedad**: el Project v2 lo crea y posee `lcasadov` (mismo owner que el repo `lcasadov/PadelPro`). `orquestadoria` está añadido como **Admin collaborator** del Project, por lo que puede crear/modificar/cerrar Issues, gestionar fields, transicionar status y añadir items al board sin necesidad de intervención humana. Todas las operaciones automáticas (inc. el agente `gh-projects-sync`) se ejecutan con el token de `orquestadoria`.

### Custom fields del Project v2

| Field | Tipo | Field ID |
|---|---|---|
| `Status` | SingleSelect | `PVTSSF_lAHOAGwvnc4BWZD6zhRtylM` |
| `Story Points` | Number | `PVTF_lAHOAGwvnc4BWZD6zhRt524` |
| `Sprint` | Number | `PVTF_lAHOAGwvnc4BWZD6zhRt528` |

### Opciones del Status field (single-select option IDs)

| Estado | Color | Option ID |
|---|---|---|
| `Backlog` | GRAY | `fb163cf2` |
| `In Progress` | YELLOW | `6ae6fd13` |
| `In Review` | ORANGE | `e07fdcb0` |
| `Done` | PURPLE | `98463b93` |

---

## Bot Orchestrator (identidad git/gh)

| Variable | Valor |
|---|---|
| Usuario | `orquestadoria` |
| Email | `orquestadoria@users.noreply.github.com` |
| Token (clásico) | `.claude/agents/.env` → `ORCHESTRATORIA_TOKEN` |
| Scopes del token | `admin:org`, `project`, `repo`, `workflow` |
| Permisos en `lcasadov/PadelPro` | `push: true` (collaborator), también ADMIN del Project v2 |

---

## Encryption (configuracion-club)

| Variable | Descripción |
|---|---|
| `ENCRYPTION_KEY` | **Obligatoria.** Clave para cifrado AES-256-GCM de secretos (telegram_bot_token, redsys_merchant_id, redsys_merchant_key). Mínimo 32 caracteres. Derivada con PBKDF2-SHA256. |
| `JWT_SECRET` | Token secreto para JWT. Mínimo 32 caracteres. |
| `JWT_EXPIRATION` | Expiración de access token en segundos (default: 3600 = 1h). |
| `JWT_REFRESH_EXPIRATION` | Expiración de refresh token en segundos (default: 604800 = 7d). |

**Requisitos de producción:**
- Todas las variables (`ENCRYPTION_KEY`, `JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`) son **OBLIGATORIAS** sin fallbacks
- Usar Secret Manager (AWS Secrets Manager, Azure Key Vault, etc) en prod
- Rotar `ENCRYPTION_KEY` requiere desencriptación + re-encriptación de datos existentes (Fase 2)

**Reglas de uso (vinculantes):**

- **Nunca** usar la cuenta personal `lcasadov` para commits, PRs, comentarios u operaciones `gh`. Toda interacción a través de `orquestadoria`.
- Verificación obligatoria antes de cualquier mutación:
  ```bash
  gh auth status 2>&1 | grep -q "orquestadoria"
  ```
- Cargar identidad en cada sesión:
  ```bash
  export GH_TOKEN=$(grep ORCHESTRATORIA_TOKEN .claude/agents/.env | cut -d= -f2)
  export GITHUB_TOKEN=$GH_TOKEN
  export GIT_AUTHOR_NAME="orquestadoria"
  export GIT_AUTHOR_EMAIL=$(grep GIT_BOT_EMAIL .claude/agents/.env | cut -d= -f2)
  export GIT_COMMITTER_NAME="orquestadoria"
  export GIT_COMMITTER_EMAIL=$GIT_AUTHOR_EMAIL
  ```

---

## Documentos del proyecto

| Documento | Contenido | Cargado por |
|---|---|---|
| `README.md` | Especificación funcional y técnica completa. | `backend-architect`, `frontend-engineer`, `devops-engineer` |
| `backlog.md` | Backlog autoritativo (Épicas, Features, US, Tickets, Sprints). Sincronizado con GitHub Projects vía `gh-projects-sync`. | `orchestrator`, `gh-projects-sync` |
| `validacion.md` | Criterios de validación funcional. | `reality-checker`, `verification-specialist` |
| `docs/PROJECT.md` | Este fichero — variables operativas. | **TODOS** los agentes |
| `docs/openapi.yaml` | API spec (cuando se inicialice). | `backend-architect`, `api-tester`, `frontend-engineer` |
| `docs/security-design.md` | Diseño de seguridad (cuando exista). | `security-auditor` |
| `docs/data-model.md` | Modelo de datos (cuando exista). | `database-optimizer`, `backend-architect` |
| `docs/TESTING-STRATEGY.md` | Estrategia de tests (la genera `test-strategist`). | `tester-tdd`, `test-runner` |
| `docs/tasks.md` | Tracker de tareas en curso (timestamps + agentes). | `orchestrator` |
| `docs/plan/plan.md` | Planes aprobados con ítems marcables. | `orchestrator` |

---

## OpenSpec

| Variable | Valor |
|---|---|
| `OPENSPEC_PATH` | `openspec/` |
| `OPENSPEC_API_PATH` | `docs/openapi.yaml` |
| Estructura | `openspec/changes/<slug>/{proposal.md, design.md, tasks.md, specs/<capability>/spec.md}` |

---

## Stack técnico (resumen — detalle en `README.md`)

| Capa | Tecnología | Versión |
|---|---|---|
| Backend | Java + Spring Boot (arquitectura hexagonal Maven multi-módulo) | Java 17, Spring Boot 3.2 |
| Frontend | React + Vite + TypeScript | React 18 |
| Base de datos | PostgreSQL + Flyway | PostgreSQL 15 |
| Infra local | Docker Compose | servicios `db`, `backend`, `frontend` |
| CI | GitHub Actions | jobs `build`, `test`, `lint` |
| Pasarela de pago | Redsys | HMAC SHA-256 + webhook |
| Bot | Telegram Bot API | OTP + grupo |

---

## Convención de ramas

| Tipo | Prefijo | Ejemplo |
|---|---|---|
| User Story / Feature | `feature/` | `feature/42-crud-patients` |
| Task técnico | `task/` | `task/43-endpoint-create-patient` |
| Bug fix | `bugfix/` | `bugfix/44-email-validation` |
| Chore / Infra | `chore/` | `chore/45-update-deps` |

- Slug en lowercase, hyphens, ≤ 50 chars.
- El número siempre es el del Issue de GitHub.
- Toda rama deriva de `BASE_BRANCH` (`develop`).

---

## Mapeo backlog → GitHub Projects

| `backlog.md` | GitHub equivalente |
|---|---|
| Épica `EP-NN` | Milestone `EP-NN <nombre>` |
| Feature `F-NN.M` | label `feature:F-NN.M` (opcional, agrupación) |
| User Story `US-NNN` | Issue título `US-NNN · <descripción>` + label `type:user-story` |
| Ticket `TICKET-NNN` | Issue título `TICKET-NNN · <descripción>` + label `type:task|chore|bug|test|refactor` |
| Sprint `Sprint N` | label `sprint:N` y campo numérico `Sprint` del Project |
| Prioridad MoSCoW | label `priority:must|should|could|wont` |
| Story Points | campo numérico `Story Points` del Project |

---

## Estado actual (2026-05-02)

- ✅ Project v2 `#1` creado bajo `lcasadov` (https://github.com/users/lcasadov/projects/1) con `orquestadoria` como Admin collaborator.
- ✅ Status field configurado: `Backlog` / `In Progress` / `In Review` / `Done`.
- ✅ Custom fields añadidos: `Story Points` (number) y `Sprint` (number).
- ✅ 6 Milestones de Épicas creados (`EP-01` … `EP-06`).
- ✅ 30+ labels canónicos creados (`type:*`, `priority:*`, `sprint:*`, `area:*`, `auto-detected`).
- ✅ 6 Issues de Sprint 1 creados (`#2` … `#7`) y añadidos al Project con `Status=Backlog`, `Sprint=1` y `Story Points` correctos.
- ✅ Project v2 temporal bajo `orquestadoria/projects/1` eliminado (ya no es necesario).
- 🔵 Pendiente: sincronizar Sprints 2 a 6 con `gh-projects-sync`.
- 🔵 Pendiente: inicializar OpenSpec (`openspec/config.yaml` + primer change para TICKET-001).
- 🔵 Pendiente: empezar implementación de TICKET-001 (scaffolding) con `devops-engineer`.
