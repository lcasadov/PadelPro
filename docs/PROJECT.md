# PadelPro — Variables operativas del proyecto

> **Fuente de verdad** que TODOS los agentes leen al inicio de cualquier operación. Si una variable no está aquí, se cancela la operación.
> Última actualización: 2026-05-02 — alta inicial post-migración Azure DevOps → GitHub Projects.

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
| `GITHUB_PROJECT_OWNER` | `orquestadoria` (user-owned; ver nota) |
| `GITHUB_PROJECT_NUMBER` | `1` |
| `GITHUB_PROJECT_ID` (GraphQL node ID) | `PVT_kwHOEBUzE84BWYUx` |
| `GITHUB_PROJECT_URL` | https://github.com/users/orquestadoria/projects/1 |

> **Nota — propietario del Project**: el Project v2 está bajo la cuenta de `orquestadoria` (no de `lcasadov`) porque GitHub solo permite a un usuario crear Projects sobre su propia cuenta. La cuenta `lcasadov` está añadida como **colaborador con rol `ADMIN`**, así que tiene control completo desde la UI. Las Issues siguen residiendo en `lcasadov/PadelPro` y se añaden al Project como items linkados — el board agrupa Issues, no las dueña.

### Custom fields del Project v2

| Field | Tipo | Field ID |
|---|---|---|
| `Status` | SingleSelect | `PVTSSF_lAHOEBUzE84BWYUxzhRtKxs` |
| `Story Points` | Number | `PVTF_lAHOEBUzE84BWYUxzhRtLQs` |
| `Sprint` | Number | `PVTF_lAHOEBUzE84BWYUxzhRtLQw` |

### Opciones del Status field (single-select option IDs)

| Estado | Color | Option ID |
|---|---|---|
| `Backlog` | GRAY | `bf489219` |
| `In Progress` | YELLOW | `612425ba` |
| `In Review` | ORANGE | `6e056ecf` |
| `Done` | PURPLE | `b9da9a00` |

---

## Bot Orchestrator (identidad git/gh)

| Variable | Valor |
|---|---|
| Usuario | `orquestadoria` |
| Email | `orquestadoria@users.noreply.github.com` |
| Token (clásico) | `.claude/agents/.env` → `ORCHESTRATORIA_TOKEN` |
| Scopes del token | `admin:org`, `project`, `repo`, `workflow` |
| Permisos en `lcasadov/PadelPro` | `push: true` (collaborator), también ADMIN del Project v2 |

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
| Backend | Java + Spring Boot (arquitectura hexagonal Maven multi-módulo) | Java 21, Spring Boot 3.2 |
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

- ✅ Project v2 creado (`#1`) con 4 columnas y custom fields `Story Points` + `Sprint`.
- ✅ 6 Milestones de Épicas creados (`EP-01` … `EP-06`).
- ✅ 30+ labels canónicos creados (`type:*`, `priority:*`, `sprint:*`, `area:*`, `auto-detected`).
- ✅ 6 Issues de Sprint 1 creados (`#2` … `#7`) y añadidos al Project con `Status=Backlog`, `Sprint=1` y `Story Points` correctos.
- 🔵 Pendiente: sincronizar Sprints 2 a 6 con `gh-projects-sync`.
- 🔵 Pendiente: inicializar OpenSpec (`openspec/config.yaml` + primer change para TICKET-001).
- 🔵 Pendiente: empezar implementación de TICKET-001 (scaffolding) con `devops-engineer`.
