---
name: technical-decisions-padelpro
description: Decisiones arquitectónicas y de proceso adoptadas en PadelPro — gestor de trabajo, branching, identidad git, sincronización de backlog
type: project
---

# PadelPro — Decisiones técnicas y de proceso

> Cada entrada lleva su fecha de adopción para juzgar relevancia futura.

## D-001 · Gestor de trabajo: GitHub Projects v2 (no ADO)

**Adoptada:** 2026-05-01.

**Decisión:** se descarta Azure DevOps como gestor de trabajo. Toda la gestión (Epics, User Stories, Tasks, Bugs, Sprints, prioridades, story points) se modela sobre **GitHub Issues + Milestones + Project v2** del mismo repositorio donde vive el código.

**Why:** centralizar gestión y código en un único proveedor (GitHub) reduce la fricción de doble fuente de verdad, simplifica la autenticación (un solo token de bot, sin MCP de ADO), y aprovecha que `backlog.md` ya está estructurado para mapear directamente a GitHub.

**How to apply:**
- Cualquier referencia a ADO en docs/agentes debe migrarse a la terminología de GitHub Projects.
- El mapeo backlog ↔ GitHub está documentado en `.claude/agents/gh-projects-sync.md` § Mapeo.
- Si emerge la necesidad de migrar de vuelta a ADO, solo cambian las variables de `docs/PROJECT.md` y los wrappers `gh_*` se sustituyen — el modelo conceptual no.

---

## D-002 · Sincronización backlog.md ↔ GitHub vía agente dedicado

**Adoptada:** 2026-05-01.

**Decisión:** se crea el agente `gh-projects-sync` como **único responsable** de reconciliar `backlog.md` con GitHub Issues/Milestones/Project v2. Ningún otro agente debe crear/cerrar Issues masivamente — solo delegar a este sincronizador.

**Why:** evitar drift entre la fuente local (`backlog.md`) y la remota (GitHub). Tener un solo punto de mutación es más fácil de auditar y de hacer idempotente. Los agentes individuales (orchestrator, backend-architect, etc.) sí pueden crear Issues puntuales (un bug, una user story nueva), pero la sincronización masiva pasa por `gh-projects-sync`.

**How to apply:**
- Cuando `backlog.md` cambie, delega siempre primero a `gh-projects-sync` antes de empezar a implementar.
- Cuando un agente QA detecte un bug, puede crear el Issue directamente con `gh issue create` — pero anótalo en `backlog.md` § 2.3 para que `gh-projects-sync` lo refleje en el Project en la próxima sincronización.

---

## D-003 · Identidad git obligatoria: `orquestadoria`

**Adoptada:** prior — heredada de la configuración inicial del repo.

**Decisión:** todos los commits, PRs, comentarios y operaciones `gh` se realizan bajo el usuario `orquestadoria`, cuyas credenciales están en `.claude/agents/.env`. **Nunca** la cuenta personal del desarrollador.

**Why:** trazabilidad limpia (todo lo que generan los agentes lleva la firma del bot, separada del trabajo manual del humano), y permite revocar el token de bot sin afectar la cuenta personal.

**How to apply:**
- Cualquier script que invoque `git`, `gh` o GraphQL de GitHub debe primero exportar `GH_TOKEN`, `GIT_AUTHOR_*`, `GIT_COMMITTER_*` desde `.claude/agents/.env`.
- Verificar `gh auth status | grep orquestadoria` antes de mutaciones.

---

## D-004 · Branching: `develop` como base, prefijos por tipo

**Adoptada:** prior — ver § 3.1 de `backlog.md`.

**Decisión:** trunk-based con `develop` como integración y `main` como release. Prefijos: `feature/` (US), `task/` (subtareas técnicas), `bugfix/`, `chore/`. Slug `<issue-number>-<slug>`.

**Why:** flujo simple de un equipo pequeño; el `<issue-number>` es siempre el de GitHub para vincular automáticamente PRs y Issues.

**How to apply:**
- Branch nunca empieza sin Issue creado.
- Slug en lowercase, ≤ 50 chars.
- Mensajes de commit incluyen `(#<ID>)` para auto-link.

---

## D-005 · Plan de ejecución obligatorio antes de actuar

**Adoptada:** prior — ver `CLAUDE.md` § Phase 0.

**Decisión:** el orquestador **nunca** ejecuta sin haber presentado un plan estructurado y recibido aprobación explícita del usuario.

**Why:** evita re-trabajo cuando el usuario detecta una mala interpretación del prompt; pone el control de scope en manos del usuario antes de gastar tokens y tiempo.

**How to apply:**
- Plan en formato fijo: tabla `| Paso | Agente | Tarea | Depende de |` + paralelismo + riesgos + fuera de alcance.
- Esperar "sí" o equivalente antes de cualquier mutación de archivos o llamada a `gh`.

---

## D-006 · Paralelismo por defecto en delegación

**Adoptada:** prior — ver `CLAUDE.md` § Phase 3.

**Decisión:** delegar agentes en paralelo siempre que no haya dependencia real entre ellos. Una sola dependencia hardcoded fuerza secuencialidad; en su ausencia, se asume paralelo.

**Why:** maximiza throughput sin coste adicional (los agentes son independientes en su ejecución).

**How to apply:**
- Lanzar múltiples llamadas `Agent` en un único mensaje cuando sean independientes.
- Solo secuencial: tests → después del código, PR → después de tests verdes, migraciones BD → antes del código que las consume, fix de bug → después del test que falla.

---

## D-007 · OpenSpec como fuente de verdad de requisitos

**Adoptada:** prior — ver `CLAUDE.md` § OpenSpec Synchronization.

**Decisión:** todo cambio funcional se documenta en `openspec/changes/<slug>/` con `proposal.md`, `design.md`, `tasks.md`, y `specs/<capability>/spec.md`. GitHub Projects es el tracker de ejecución; OpenSpec es el de requisitos.

**Why:** separar "qué hay que hacer y por qué" (OpenSpec) de "quién lo hace y cuándo" (GitHub Projects). El primero es estable y reusable, el segundo es operacional y ruidoso.

**How to apply:**
- Task IDs en `openspec/changes/<slug>/tasks.md` referencian Issues de GitHub: `- [ ] 1.1 (#43) Implementar endpoint POST /patients`.
- Antes de cerrar la PR, verificar que `tasks.md` del change está al 100%.
