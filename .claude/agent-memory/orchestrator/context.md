---
name: project-context-padelpro
description: Contexto general del proyecto PadelPro — dominio, stack técnico, gestor de trabajo, agentes activos, identidad git
type: project
---

# PadelPro — Contexto del proyecto

## Dominio funcional

Sistema de gestión de reservas de pádel para un club con una sola pista. Cubre:

- **Acceso e identidad** (EP-01): login web, recuperación de contraseña con OTP, alta de jugadores, gestión de cuentas por admin.
- **Reservas** (EP-02): visualización de disponibilidad, creación con participantes, confirmación con OTP por Telegram, cancelación, "unirse a reserva incompleta", calendario semanal, historial.
- **Pagos** (EP-03): integración con **Redsys** (HMAC SHA-256 + webhook) para pago online y registro manual de pago en efectivo.
- **Bot Telegram** (EP-04): reserva, cancelación y notificaciones de grupo vía bot.
- **Panel admin** (EP-05): gestión de usuarios, dashboard con calendario y gráficas, configuración de precio/políticas.
- **Notificaciones** (EP-06): alertas automáticas vía Telegram al crear/cancelar reservas.

## Stack técnico

- **Backend:** Java 21 · Spring Boot 3.2 · arquitectura hexagonal (módulos Maven `domain` / `application` / `infrastructure`).
- **Frontend:** React 18 · Vite · TypeScript.
- **Base de datos:** PostgreSQL 15 · Flyway para migraciones.
- **Infra local:** Docker Compose (servicios `db`, `backend`, `frontend`).
- **CI:** GitHub Actions (jobs `build`, `test`, `lint`).
- **Integraciones:** Redsys (pasarela de pago), Telegram Bot API (OTP + grupo).

## Documentación autoritativa

| Documento | Contenido |
|---|---|
| `README.md` | Especificación funcional y técnica completa. |
| `backlog.md` | Backlog autoritativo: épicas, features, US, tickets, sprints. Fuente de verdad para la planificación. |
| `validacion.md` | Criterios de validación del proyecto. |
| `docs/PROJECT.md` | Variables de entorno y rutas (existir o crear cuando se inicialice el proyecto). |
| `CLAUDE.md` | Guía operativa del orquestador (tú). |

## Gestión de trabajo — GitHub Projects

- **Ya no se usa Azure DevOps.** El proyecto pasó a **GitHub Projects v2** como único gestor de Issues, Milestones y Project board.
- **Fuente de verdad local:** `backlog.md`. **Fuente de verdad remota:** GitHub Issues + Project v2.
- **Sincronización:** delegada al agente `gh-projects-sync` (ver `.claude/agents/gh-projects-sync.md`).
- **Variables clave** (de `docs/PROJECT.md`): `GITHUB_ORG`, `GITHUB_REPO`, `GITHUB_PROJECT_NUMBER`, `BASE_BRANCH`, `REPO_ROOT`.

### Mapeo backlog → GitHub

- Épica `EP-NN` → Milestone `EP-NN <nombre>`.
- User Story `US-NNN` → Issue `US-NNN · <descripción>` con label `type:user-story`.
- Ticket `TICKET-NNN` → Issue con label `type:task` (o `type:bug` / `type:chore` / `type:test` / `type:refactor`).
- Sprint → label `sprint:N`.
- Prioridad MoSCoW → label `priority:must|should|could|wont`.
- Story Points → campo numérico custom `Story Points` en el Project v2.
- Status: Backlog · Refinement · Sprint Backlog · In Progress · In Review · Done.

## Identidad git — `orquestadoria`

- **NUNCA** usar la cuenta personal del usuario para operaciones git/gh.
- Token en `.claude/agents/.env` (`ORCHESTRATORIA_TOKEN`).
- Email en `.claude/agents/.env` (`GIT_BOT_EMAIL`).
- Scopes requeridos: `repo`, `project`, `workflow`.
- Verificar identidad con `gh auth status | grep orquestadoria` antes de cualquier acción.

## Agentes activos (`.claude/agents/`)

| Agente | Rol |
|---|---|
| `orchestrator` | Coordinador (este agente). |
| `backend-architect` | Backend Spring Boot, hexagonal, JPA, security, OpenAPI. |
| `frontend-engineer` | React + TS, formularios, estado, integración API. |
| `devops-engineer` | CI/CD GitHub Actions, Docker, infra. |
| `database-optimizer` | N+1, índices, migraciones Flyway. |
| `tester-tdd` | TDD greenfield + estrategia de tests. |
| `test-runner` | Tests sobre código ya implementado. |
| `verification-specialist` | Verificación adversarial PASS/FAIL/PARTIAL. |
| `reality-checker` | Puerta final antes de PR (NEEDS WORK por defecto). |
| `api-tester` | Validación funcional + OWASP API + perf endpoints REST. |
| `security-auditor` | OWASP Top 10, RBAC, JWT, dependencias CVE. |
| `pull-requests` | Creación y validación de PRs. |
| `gh-projects-sync` | Sincronización backlog.md ↔ GitHub Projects. |

## Convenciones de branching

- Base branch: `develop` (`main` queda como rama protegida de releases).
- Ramas siempre derivadas de `develop`, con prefijo según tipo:
  - `feature/<issue-number>-<slug>`
  - `task/<issue-number>-<slug>`
  - `bugfix/<issue-number>-<slug>`
  - `chore/<issue-number>-<slug>`
- Slug: lowercase, hyphens, ≤ 50 caracteres.
- **Toda tarea empieza creando rama desde `develop`** — nunca tocar código sin rama dedicada.

## OpenSpec

- Path: `openspec/` (default; configurable en `docs/PROJECT.md`).
- Cada cambio funcional crea o actualiza un folder en `openspec/changes/<slug>/` con `proposal.md`, `design.md`, `tasks.md` y `specs/<capability>/spec.md`.
- Task IDs en `tasks.md` deberían referenciar el número de Issue de GitHub (ej. `- [ ] 1.1 (#43) ...`).
