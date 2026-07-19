# Tracker de tareas en curso

> Última actualización: 2026-07-19 (orchestrator)

## Estado actual

**No hay tareas de implementación en curso.** Rama `develop` al día; último trabajo mergeado: **#240** — blindaje de login contra fuerza bruta (XFF spoofing + account lockout).

Las 14/14 capabilities del MVP están implementadas y archivadas en OpenSpec (ver `docs/plan/plan.md` y `openspec/changes/archive/`).

## Actividad reciente

### 2026-07-19 — Backfill de trazabilidad task ↔ PR

| Tarea | Estado | Agente | Inicio | Fin | Detalle |
|-------|--------|--------|--------|-----|---------|
| Auditar tasks sin PR vinculada | ✅ Completada | orchestrator | 2026-07-19 | 2026-07-19 | 56/63 tasks `type:task` no tenían PR (el `Closes #` iba a la US padre) |
| Backfill comentario de trazabilidad en 56 tasks | ✅ Completada | orchestrator | 2026-07-19 | 2026-07-19 | 37 confirmadas (T-0xx → #77/#89) + 19 probables |
| Arqueología de las 13 probables | ✅ Completada | orchestrator | 2026-07-19 | 2026-07-19 | Ver correcciones abajo |

**Correcciones tras verificar código real:**
- **#4** (dominio/puertos reservas) → enlace real **#157** (no #162).
- **#39, #40** (bot Telegram crear/cancelar reserva) → **descope**: no implementadas como se plantearon. El webhook Telegram solo procesa `/vincular` (OTP); crear/cancelar es solo por web.
- **#34, #48** → confirmadas (#208 y #151/#155/#229).

### 2026-07-19 — Consolidación de backlog duplicado

Resuelta la duplicación entre el backlog original (US-001..025 #8–#32) y las issues re-escopadas modernas (#144+). Se comentó y etiquetó cada original con su equivalente canónico.

| Grupo | Nº | Label | Detalle |
|-------|----|-------|---------|
| US re-escopadas | 19 (#8–#32, sin #13/#14/descopes) | `superseded` | Enlazadas a la US/PR moderna que implementó el trabajo |
| Descopes | 5 (#24, #25, #26 US + #39, #40 tasks) | `wontfix` | Integración Telegram para crear/cancelar/confirmar reserva: descartada. Telegram = OTP entrante (`/vincular`) + notificaciones salientes; reservas por web |

Filtrable en GitHub: `label:superseded` (19) y `label:wontfix` (5). Nota: #13 (US-006) y #14 (US-007) ya tenían PR directa (#157/#162), no son duplicados.

### 2026-07-19 — Links PR nativos en el tablero (Project column)

Descubierto que un comentario mencionando `#PR` **no** rellena la columna "linked PR" del Project — solo lo hace un *closing reference* nativo (`Closes #N` en el cuerpo de la PR). Se editó el cuerpo de **22 PRs mergeadas** añadiendo `Closes #N` para las ~45 tasks/bugs/US canónicas.

| Grupo | Detalle |
|-------|---------|
| Tasks/bugs canónicos vinculados | ~45 issues → su PR real (verificado por GraphQL `closedByPullRequestsReferences`) |
| #181, #199 (OPEN) | Vinculados a #218/#219 y **cerrados** (trabajo terminado) |
| #51 observabilidad | Vinculado a #155 (parcial: logging básico + actuator sí; logging estructurado JSON **no** implementado) |
| #15, #25, #26, #39, #40 | `wontfix` (integración Telegram crear/cancelar/confirmar reserva: descartada) |

**Estado final de trazabilidad** — issues sin PR nativa restantes son solo legítimos: 42 `duplicate`, contenedores (27 features + 9 épicas + 10 stories), 19 `superseded`, 6 `wontfix`. Cero tasks/bugs canónicos sin PR.

## Notas de proceso

- **Regla de trazabilidad** (2026-07-19): toda task futura debe referenciarse en su PR (`Closes #<task>`), no solo la User Story padre. Ver memoria `feedback_task_pr_traceability`.
- **Pendiente opcional**: consolidar duplicación de backlog — el original US-001..025 (#8–#32) + TICKET-003..023 (#4–#49) se solapa con issues re-escopadas más nuevas (#144+); varias US viejas también quedaron sin PR por el mismo motivo.
