# AGENTS.md — Guía para agentes LLM en PadelPro OpenSpec

> Este documento es la referencia obligatoria para cualquier agente que trabaje con el OpenSpec de PadelPro.
> Léelo completo antes de crear, modificar o consultar cualquier artefacto del directorio `openspec/`.

---

## 1. Orden de lectura obligatorio

Antes de escribir cualquier código, spec o artefacto, lee los documentos en este orden. Los de mayor numeración tienen mayor precedencia en caso de conflicto:

| Orden | Documento | Qué extraer |
|---|---|---|
| 1 | `docs/PROJECT.md` | Variables operativas: `REPO_ROOT`, `BASE_BRANCH`, `GITHUB_ORG`, `GITHUB_REPO`, `GITHUB_PROJECT_NUMBER`, stack técnico, rutas de documentos. |
| 2 | `docs/openapi.yaml` | Contrato REST canónico: rutas exactas (base `/api`), esquemas de request/response, códigos de estado, operationIds. |
| 3 | `docs/security-design.md` | Reglas de seguridad autoritativas: RBAC, JWT (HS256), CORS, rate limiting, aislamiento de datos. |
| 4 | `docs/data-model.md` | Entidades, tablas, campos, tipos, índices y constraints de la BD. |
| 5 | `openspec/config.yaml` | Configuración del proyecto OpenSpec: stack, roles, fases, reglas por artefacto. |
| 6 | `openspec/project.md` | Contexto de dominio: glosario, capabilities, fases del producto, flujos críticos, reglas de negocio. |
| 7 | `openspec/changes/<slug>/proposal.md` | Propuesta del change activo: por qué, qué cambia, impacto. |
| 8 | `openspec/changes/<slug>/design.md` | Decisiones de diseño del change activo. |
| 9 | `openspec/changes/<slug>/specs/<capability>/spec.md` | Requisitos detallados y scenarios BDD del change activo. |
| 10 | `openspec/changes/<slug>/tasks.md` | Checklist de tareas del change activo. |

> **`README.md`** es contexto general de producto. Léelo para entender el dominio. En caso de conflicto con `docs/`, los documentos en `docs/` siempre tienen precedencia.

---

## 2. Qué es una capability

Una **capability** es una agrupación funcional cohesiva del sistema que puede implementarse, probarse y documentarse de forma independiente. Corresponde a un módulo de negocio o un servicio transversal.

Cada capability tiene:
- Un **ID único** en `openspec/config.yaml` (ej. `auth-local`, `reservas`, `pagos-redsys`).
- Un **spec base** en `openspec/specs/<capability>/spec.md` que describe los requisitos permanentes.
- Cero o más **specs de change** en `openspec/changes/<slug>/specs/<capability>/spec.md` que añaden, modifican o eliminan requisitos para un change concreto.

Las capabilities no mapean 1:1 con módulos Java. Por ejemplo, la capability `auth-otp-telegram` afecta a los módulos `usuarios`, `otp` y `mensajeria` del backend.

---

## 3. Qué es un change

Un **change** es una unidad de trabajo planificada que modifica uno o más capabilities. Cada change tiene su propio directorio en `openspec/changes/<slug>/` con cuatro artefactos obligatorios:

```
openspec/changes/<slug>/
├── proposal.md    ← Por qué (motivación, objetivos, impacto, fuera de alcance)
├── design.md      ← Cómo (decisiones de diseño, riesgos, plan de migración)
├── tasks.md       ← Checklist de tareas por capa (Backend / Frontend / Testing / Infra)
└── specs/
    └── <capability>/
        └── spec.md  ← Requisitos AÑADIDOS/MODIFICADOS/ELIMINADOS por este change
```

Los specs de change son **deltas sobre los specs base**. Solo describen lo que cambia. Al archivar el change (`/opsx:archive`), los deltas se fusionan con los specs base.

---

## 4. Reglas para agentes

1. **Nunca modifiques un spec sin un change activo.** Toda modificación de comportamiento del sistema debe tener un `openspec/changes/<slug>/` con sus cuatro artefactos. Si no existe, créalo con `/opsx:propose <slug>` antes de implementar.

2. **Referencia siempre las reglas de negocio por su código exacto.** Usa `RN-AUTH-06`, `RN-PAY-01`, etc. — nunca parafrasees la regla sin citar el código. Los códigos están en `openspec/project.md §8` y en `docs/security-design.md`.

3. **Usa solo los roles de v1.0 en las matrices RBAC.** Los roles válidos son `ADMIN` y `USER`. Nunca escribas `MANAGER_CLUB`, `JUGADOR` o `INVITADO` en specs, matrices de permisos o scenarios de la Fase 1.

4. **Los paths de la API provienen exclusivamente de `docs/openapi.yaml`.** No inventes rutas. El base path es `/api` (no `/api/v1`). Si necesitas un endpoint nuevo, primero propón el cambio en `docs/openapi.yaml` como parte del change.

5. **Los scenarios BDD usan el formato Given/When/Then estricto.** No uses prosa narrativa. Cada step comienza con `GIVEN`, `WHEN`, `THEN` o `AND`. El sistema es el sujeto del `THEN`, no el usuario.

6. **No incluyas código, DDL ni detalles de implementación en los specs.** Los specs describen comportamiento observable desde la API o el dominio. El cómo se implementa va en `design.md` o en el código fuente.

7. **Registra los conflictos como comentarios antes de proceder.** Si encuentras una contradicción entre dos fuentes (ej. `README.md` dice algo diferente a `docs/openapi.yaml`), documenta el conflicto como comentario HTML en el artefacto afectado y notifica al orquestador antes de continuar.

8. **Los specs de change son deltas, no copias.** En `openspec/changes/<slug>/specs/<capability>/spec.md` solo escribas los requisitos nuevos o modificados. Marca cada requisito con `[AÑADIDO]`, `[MODIFICADO]` o `[ELIMINADO]` respecto al spec base.

---

## 5. Mapeo con GitHub Projects

Cada change en OpenSpec corresponde a uno o más Issues en GitHub Projects v2 (`lcasadov/PadelPro`, Project #1):

| Elemento OpenSpec | GitHub equivalente |
|---|---|
| Change `<slug>` | Issue con label `type:user-story` o `type:task` |
| Tarea en `tasks.md` | Sub-issue o checkbox en el body del Issue padre |
| Capability afectada | Label `area:<capability>` en el Issue |
| Fase del change | Milestone `EP-NN <nombre>` del Issue |

**Convención de nombre de rama:** `feature/<issue-number>-<slug>` (ej. `feature/42-auth-local`).
**Referencia en commit:** `feat(auth-local): implementar login (#42)`.
**Auto-cierre:** `Closes #42` en el cuerpo de la PR cierra el Issue al mergear.

El agente `gh-projects-sync` es el responsable de mantener sincronizados los Issues de GitHub con el estado de los changes en OpenSpec.

---

## 6. Coherencia con docs/

| Pregunta | Fuente autoritativa |
|---|---|
| ¿Qué endpoints existen y con qué paths? | `docs/openapi.yaml` |
| ¿Qué rol puede hacer qué? | `docs/security-design.md §3` |
| ¿Cómo se almacena el dato X? | `docs/data-model.md` |
| ¿Qué algoritmo de firma usa el JWT? | `docs/security-design.md §2.2` (HS256, no RS256) |
| ¿Cuánto dura el access token? | `docs/security-design.md §2.3` (15 min, no 8h como indica el README) |
| ¿Cuál es el base path de la API? | `docs/openapi.yaml servers` (`/api`, no `/api/v1`) |
| ¿Cuántas pistas tiene la instalación? | `README.md §3.2` + `openspec/project.md` (una sola pista en v1.0) |
| ¿Qué datos personales son RGPD? | `docs/security-design.md §11.1` |

> **Conflicto conocido documentado:** `README.md §3.7` indica 8h para el access token. `docs/security-design.md §2.3` lo corrige a 15 min. La fuente autoritativa es `security-design.md`.

---

## 7. Cuando detectes una inconsistencia

Si al leer los documentos encuentras una contradicción entre dos fuentes:

1. **Identifica la precedencia**: `docs/openapi.yaml` > `docs/security-design.md` > `docs/data-model.md` > `README.md` > cualquier otro documento.
2. **No asumas**: no implementes la versión que te parece correcta sin documentar la inconsistencia.
3. **Documenta el conflicto** en el artefacto más cercano al problema:
   ```html
   <!-- CONFLICTO: README.md §3.7 indica 8h para access token.
        docs/security-design.md §2.3 indica 15 min.
        Fuente autoritativa: security-design.md (15 min).
        Notificado al orquestador: 2026-05-22. -->
   ```
4. **Notifica al orquestador** en tu mensaje de retorno con: ficheros en conflicto, valores contradictorios, fuente autoritativa elegida y razón.
5. **Continúa con la fuente de mayor precedencia** si el orquestador no responde en el mismo turno y el conflicto no bloquea la tarea.
