# Proposal: Bootstrap MVP

**Change slug:** `bootstrap-mvp`
**Estado:** proposed
**Issue GitHub:** #76
**Épica:** EP-01 Acceso e Identidad
**Milestone:** EP-01 Acceso e Identidad

---

## Contexto

PadelPro tiene ya documentados su modelo de datos (`docs/data-model.md`), su diseño de seguridad (`docs/security-design.md`), su contrato REST (`docs/openapi.yaml`) y su estrategia de testing (`docs/TESTING-STRATEGY.md`). La documentación UX está completa en `docs/ux/`. Sin embargo, no existe todavía ningún change OpenSpec ejecutable que un agente pueda tomar para iniciar la implementación.

Este change resuelve ese bloqueo de dos maneras:

1. **Andamiaje**: consolida el esqueleto OpenSpec (`openspec/project.md`, `openspec/AGENTS.md`) como punto de entrada canónico para todos los agentes futuros.
2. **Primera capability ejecutable**: declara `auth-local` mínima — registro e inicio de sesión con email y contraseña, emisión de JWT, rate limiting y auditoría de intentos — como la unidad mínima sobre la que se puede construir todo lo demás.

Sin una identidad de usuario autenticada, ninguna otra capability puede operar: las reservas necesitan un `owner_id`, los pagos necesitan un titular, las notificaciones necesitan un `telegram_chat_id`. Este change crea ese prerrequisito.

---

## Alcance dentro del change

- Verificación y referenciación de `openspec/project.md` y `openspec/AGENTS.md` (ya existen; no se modifican).
- Capability `auth-local` mínima viable, que cubre:
  - Registro de usuario con email único y contraseña (validación de política).
  - Login con credenciales (email + contraseña) → devuelve access token JWT (HS256).
  - Emisión y validación de tokens JWT (access 15 min en memoria JS, refresh 7 días en cookie httpOnly).
  - Rate limiting en endpoints públicos de auth (login 5/min/IP, registro 3/min/IP).
  - Registro de auditoría de cada intento de login (éxito y fallo) en `audit_log`.
- Trazabilidad completa a mockups UX: `07-splash`, `01-login`, `08-crear-cuenta`.

---

## Alcance fuera del change

Los siguientes aspectos de `auth-local` **no** forman parte de este change y se declaran en changes posteriores:

| Capacidad excluida | Change futuro |
|---|---|
| Recuperación de contraseña (OTP por Telegram) | `auth-password-reset` |
| Verificación de email al registrarse | `auth-email-verification` |
| Vinculación de cuenta con Telegram | `auth-otp-telegram` |
| Refresco de token y logout | `auth-session-management` |
| Perfil del usuario (nombre, teléfono) | `usuarios` |
| Gestión de usuarios por ADMIN | `usuarios` |
| Bloqueo de cuenta tras fallos repetidos (RN-AUTH-06) | `auth-lockout` |

---

## Criterios de aceptación

1. Los artefactos `openspec/project.md` y `openspec/AGENTS.md` existen y son referenciados desde este change.
2. El spec de `auth-local` en este change declara los requirements mínimos (R-1 a R-5) con scenarios Given/When/Then sin ambigüedad: un agente de backend puede convertirlos en tests sin necesidad de interpretar.
3. Existe trazabilidad explícita a los mockups UX `07-splash.html`, `01-login.html` y `08-crear-cuenta.html` en `docs/ux/mockups/`.
4. Las decisiones de diseño en `design.md` son consistentes con las reglas de negocio autoritativas: RN-AUTH-08 (contraseña), RN-AUTH-09 (tokens), RN-SEC-01 (rate limiting), y con el algoritmo HS256 documentado en `docs/security-design.md`.
5. La lista de tareas en `tasks.md` es ejecutable sin ambigüedad: un agente `backend-architect` puede tomar el bloque BACKEND y empezar sin preguntar.

---

## Riesgos

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| Ambigüedad en la política de contraseñas (el prompt de diseño mencionó "símbolo" como requisito adicional, pero RN-AUTH-08 solo exige mayúscula + número) | Media | `design.md` sigue la fuente autoritativa (RN-AUTH-08). Se documenta el conflicto. Requiere validación humana. |
| Elección entre BCrypt y Argon2id | Baja | `design.md` recomienda BCrypt cost 12 (ya especificado en RN-AUTH-08). Argon2id documentado como alternativa. |
| Incompatibilidad de H2 con constraints PostgreSQL en tests | Alta | `docs/TESTING-STRATEGY.md` ya lo documenta. Testcontainers obligatorio para tests de integración. |
| Solapamiento con el change `auth-otp-telegram` | Media | Este change solo declara JWT/contraseña. Ningún scenario menciona Telegram. La frontera es clara. |

---

## Impacto en otros changes

Este change es el cimiento de todos los changes de Fase 1:

| Change futuro | Dependencia de `bootstrap-mvp` |
|---|---|
| `auth-otp-telegram` | Extiende la identity creada aquí con `telegram_chat_id`. |
| `usuarios` | Consume el modelo `User` (id, email, role) definido en este change. |
| `reservas` | Requiere `owner_id` (FK a `users.id`) establecida aquí. |
| `pagos-redsys` | Requiere un titular autenticado (USER role). |
| `roles-permisos` | La matriz RBAC opera sobre los roles ADMIN/USER definidos en `users.role`. |
| `auditoria` | El registro de intentos de login (R-5 de este change) es la primera entrada en `audit_log`. |
| `exportaciones-rgpd` | El derecho de anonimización opera sobre el `users.id` establecido aquí. |

**Sin este change aprobado y ejecutado, ningún otro change de Fase 1 puede entrar en `in-progress`.**
