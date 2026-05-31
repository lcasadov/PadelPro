# Plan de ejecución — PadelPro OpenSpec

**Generado:** 2026-05-31  
**Base:** dependencias declaradas en `openspec/specs/*/spec.md`  
**Estado:** 2/14 capabilities implementadas

---

## Estado actual

| Capability | Estado | Change archivado |
|---|---|---|
| `auth-local` | ✅ Implementada | `archive/2026-05-31-bootstrap-mvp` |
| `usuarios` | ✅ Implementada | `archive/2026-05-31-usuarios` |
| `roles-permisos` | 📋 Pendiente | — |
| `auditoria` | 📋 Pendiente | — |
| `configuracion-club` | 📋 Pendiente | — |
| `pistas` | 📋 Pendiente | — |
| `disponibilidad-pistas` | 📋 Pendiente | — |
| `auth-otp-telegram` | 📋 Pendiente | — |
| `reservas` | 📋 Pendiente | — |
| `pagos-redsys` | 📋 Pendiente | — |
| `notificaciones` | 📋 Pendiente | — |
| `partidas` | 📋 Pendiente | — |
| `exportaciones-rgpd` | 📋 Pendiente | — |
| `administracion-club` | 📋 Pendiente (Fase 2) | — |

---

## Grafo de dependencias

```
┌─────────────────────────────────────────────────────────────────┐
│  DONE                                                           │
│  auth-local ✅          usuarios ✅                             │
└──────────────┬──────────────────────┬──────────────────────────┘
               │                      │
               ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  WAVE 1 — Infraestructura transversal  (paralelo)               │
│                                                                 │
│  roles-permisos ──────── (auth-local + usuarios → todos)        │
│  auditoria ────────────── (auth-local → tabla ya existe)        │
│  configuracion-club ───── (roles-permisos → system_config)      │
│  pistas ───────────────── (configuracion-club → estado pista)   │
└──────────────────────────────────────┬──────────────────────────┘
                                       │
               ┌───────────────────────┤
               │                       │
               ▼                       ▼
┌──────────────────────────┐  ┌────────────────────────────────┐
│  WAVE 2A                 │  │  WAVE 2B                        │
│  disponibilidad-pistas   │  │  auth-otp-telegram              │
│  (configuracion-club +   │  │  (usuarios + otp_codes tabla)   │
│   reservations schema)   │  │                                 │
└────────────┬─────────────┘  └──────────────┬──────────────────┘
             │                               │
             └──────────────┬────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  WAVE 3 — NÚCLEO (secuencial, mayor complejidad)                │
│                                                                 │
│  reservas                                                       │
│  (usuarios + configuracion-club + disponibilidad-pistas)        │
│  → SELECT FOR UPDATE + EXCLUDE USING gist                       │
│  → crea reservations + payments atómicamente                    │
└──────────────────────────────────────┬──────────────────────────┘
                                       │
         ┌─────────────────────────────┼────────────────────┐
         │                             │                    │
         ▼                             ▼                    ▼
┌─────────────────┐     ┌──────────────────────┐  ┌───────────────┐
│  WAVE 4A        │     │  WAVE 4B             │  │  WAVE 4C      │
│  pagos-redsys   │     │  notificaciones      │  │  partidas     │
│  (reservas +    │     │  (reservas +         │  │  (reservas +  │
│   conf-club)    │     │   auth-otp-telegram) │  │   disp-pistas)│
└─────────────────┘     └──────────────────────┘  └───────────────┘
         │                             │                    │
         └─────────────────────────────┼────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  WAVE 5                                                         │
│  exportaciones-rgpd                                             │
│  (usuarios + auditoria + reservas + pagos-redsys)               │
└──────────────────────────────────────┬──────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  WAVE 6 — Fase 2 (frontend-heavy)                               │
│  administracion-club                                            │
│  (reservas + pagos-redsys + auditoria + exportaciones-rgpd)     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Ondas de implementación

### Wave 1 — Infraestructura transversal

**Paralelismo:** las 4 capabilities pueden implementarse en paralelo o en 2 pares.  
**Bloqueante para:** todo lo demás.

| Capability | Descripción | SP | Nota |
|---|---|---|---|
| `roles-permisos` | Formalizar RBAC; Spring Security ya implementa `hasRole('ADMIN')`. Añadir reglas granulares si el spec lo requiere. | 2 | Mayor parte ya implementada en `SecurityConfig` |
| `auditoria` | `GET /api/admin/audit` + filtros por acción/usuario/fecha. Tabla `audit_log` ya existe (V3). | 3 | Solo la capa de lectura |
| `configuracion-club` | `GET/PATCH /api/admin/sistema/config`. Flyway V5: tabla `system_config` singleton. **⚠️ Requiere decisión de diseño** sobre `ENCRYPTION_KEY`. | 5 | AES-256-GCM para secretos |
| `pistas` | Estado operativo de la pista única (ACTIVA/MANTENIMIENTO) vía `system_config`. Solapamiento con `configuracion-club`. | 2 | Puede integrarse en el mismo change que `configuracion-club` |

**⚠️ Decisión de diseño necesaria antes de Wave 1:**
> **D-CONF-01** — ¿Dónde vive la clave `ENCRYPTION_KEY` para AES-256-GCM de `system_config`?  
> Opciones: (A) env var `ENCRYPTION_KEY` sin fallback, igual que `JWT_SECRET`. (B) derivada de `JWT_SECRET` (no recomendado — mezcla propósitos). (C) KMS (overkill v1.0).  
> **Recomendación:** opción A. Decidir antes de crear el change de `configuracion-club`.

---

### Wave 2 — Pre-núcleo (paralelo)

**Paralelismo:** 2A y 2B son independientes entre sí.

#### Wave 2A — `disponibilidad-pistas`

Requiere Wave 1 completa (específicamente `configuracion-club` para leer `system_config.max_participants` y estado de la pista).

**⚠️ Decisión de diseño necesaria antes de Wave 2A:**
> **D-RES-01** — Schema de la tabla `reservations`: ¿`tsrange` + `EXCLUDE USING gist` o columnas `(start_time, duration_minutes)` con constraint calculado?  
> Esta decisión es un **punto de no retorno**: si después de añadir datos de producción se cambia el schema, la migración puede fallar por conflictos en el constraint gist.  
> La migración Flyway que crea `reservations` debe estar correcta desde el primer día.  
> **Recomendación:** `tsrange` + `EXCLUDE USING gist` (más robusto para el anti-solapamiento de Wave 3).

La implementación de `disponibilidad-pistas` crea la migración Flyway de `reservations` (vacía) para poder consultar disponibilidad desde el primer día, incluso antes de que `reservas` esté implementado.

#### Wave 2B — `auth-otp-telegram`

Independiente de Wave 2A. Requiere Wave 1 (`configuracion-club` para `telegram_bot_token`).

**⚠️ Decisión de diseño necesaria antes de Wave 2B:**
> **D-OTP-01** — ¿Cómo se valida el webhook de Telegram?  
> El `telegram_bot_token` de `system_config` se descifra en runtime con `ENCRYPTION_KEY`. El webhook handler necesita el token disponible antes de poder validar las peticiones entrantes.  
> Confirmar que el arrange de startup (carga del token desde BD al arrancar) está definido.

---

### Wave 3 — RESERVAS (secuencial, el núcleo del producto)

**Bloquea:** Wave 4 completa.  
**Complejidad más alta del proyecto** — 8 SP.

Requiere Wave 1 + Wave 2A. `auth-otp-telegram` (2B) no es estrictamente necesario para crear/cancelar reservas vía web, pero sí para confirmar vía bot.

**⚠️ Decisión de diseño necesaria antes de Wave 3:**
> **D-RES-02** — ¿Cómo manejar `payment_gateway` en la creación atómica de pagos?  
> Si `system_config.payment_gateway = REDSYS`, la reserva crea un `payments` record con `status=PENDING` y devuelve la URL de pago Redsys. Si `payment_gateway = CASH`, el registro se crea en `PENDING` pero el ADMIN lo confirma manualmente.  
> Esta decisión determina si `reservas` puede probarse end-to-end sin tener Redsys configurado.  
> **Recomendación:** soportar `CASH` en v1.0 como bypass para pruebas. Redsys se activa cuando `pagos-redsys` esté implementado.

> **D-RES-03** — Confirmación de reserva sin Telegram OTP:  
> El ADMIN puede usar `PATCH /api/admin/reservas/{id}/estado` para confirmar manualmente (bypass de OTP). Esto permite tener un MVP funcional antes de implementar `auth-otp-telegram`.

**Flyway migrations requeridas en Wave 3:**
- V6: `reservations` (si no creada en Wave 2A), `participants`, `payments`

---

### Wave 4 — Dependientes de reservas (paralelo)

#### Wave 4A — `pagos-redsys`

Redsys HMAC SHA-256, webhooks `POST /api/pagos/webhook`. Alta complejidad de integración externa.  
Requiere: Wave 3 + `configuracion-club` (credenciales Redsys descifradas).

#### Wave 4B — `notificaciones`

Envío de mensajes Telegram y email. Abstracción `MensajeriaPort`.  
Requiere: Wave 3 (eventos `RESERVATION_CREATED`, `RESERVATION_CANCELLED`), `auth-otp-telegram` (para password reset OTP).

#### Wave 4C — `partidas`

Vista de reservas joinables (plazas libres en reservas activas).  
Requiere: Wave 3 + `disponibilidad-pistas`.  
Baja complejidad — reutiliza entidades ya existentes.

---

### Wave 5 — `exportaciones-rgpd`

Anonimización completa de datos personales (extends `DELETE /api/admin/usuarios/{id}`).  
Requiere: Wave 4 completa (para limpiar FK en reservas y pagos).  
Genera `USER_ANONYMIZED` en `audit_log`.

---

### Wave 6 — `administracion-club` (Fase 2)

Dashboard admin: calendario de ocupación, gráficas de ingresos, exportación CSV.  
Frontend-heavy. Requiere todo lo anterior.

---

## Resumen de waves y paralelismo

```
Wave 1  ──── roles-permisos ──┐
        ──── auditoria ───────┤  (paralelo)
        ──── configuracion-club + pistas ──┘
                │
Wave 2  ────────┼──── disponibilidad-pistas (2A) ──┐  (paralelo)
                └──── auth-otp-telegram    (2B) ──┘
                │
Wave 3  ────────└──── reservas  ← SERIALIZADO, más complejo
                │
Wave 4  ────────┼──── pagos-redsys   (4A) ──┐
                ├──── notificaciones (4B) ───┤  (paralelo)
                └──── partidas       (4C) ──┘
                │
Wave 5  ────────└──── exportaciones-rgpd  ← SERIALIZADO
                │
Wave 6  ────────└──── administracion-club  ← Fase 2
```

**Mínimo MVP funcional** (alguien puede reservar y pagar en efectivo):
`Wave 1` + `Wave 2A` + `Wave 3` + conf manual de pago → ~18 SP

**MVP completo con Telegram y Redsys**:
`Wave 1` + `Wave 2` + `Wave 3` + `Wave 4` → ~44 SP

---

## Decisiones de diseño pendientes (bloqueantes)

| ID | Capability | Decisión | Urgencia |
|---|---|---|---|
| D-CONF-01 | `configuracion-club` | `ENCRYPTION_KEY` para AES-256-GCM: env var sin fallback | Antes de Wave 1 |
| D-RES-01 | `disponibilidad-pistas` | Schema `reservations`: `tsrange + gist` vs columnas simples | Antes de Wave 2A (punto de no retorno) |
| D-OTP-01 | `auth-otp-telegram` | Validación webhook: carga de token desde BD en startup | Antes de Wave 2B |
| D-RES-02 | `reservas` | `payment_gateway=CASH` como bypass para testing sin Redsys | Antes de Wave 3 |
| D-RES-03 | `reservas` | Confirmación manual de reserva por ADMIN (sin OTP Telegram) | Antes de Wave 3 |

---

## Correspondencia con Sprints del backlog

| Sprint | Objetivo | Capabilities involucradas |
|---|---|---|
| **Sprint 1** (parcial) | Fundación + visualización | ~~auth-local✅~~ + ~~usuarios✅~~ + `roles-permisos` + `disponibilidad-pistas` + `configuracion-club` |
| **Sprint 2** | Reservas core web | `reservas` + `auth-otp-telegram` (confirmación) + `notificaciones` (publicación Telegram) |
| **Sprint 3** | Bot Telegram + Pagos | `auth-otp-telegram` (bot completo) + `pagos-redsys` + `notificaciones` completo |
| **Sprint 4** | MVP completo | `partidas` + `auth-otp-telegram` (reset password) + completar flujos |
| **Sprint 5** | Calidad y admin | `exportaciones-rgpd` + `auditoria` (read API) + `administracion-club` |

---

## Próximas acciones recomendadas

1. **Resolver D-CONF-01** — Confirmar que `ENCRYPTION_KEY` será env var sin fallback (igual que `JWT_SECRET`). Documentar en `docs/security-design.md` si existe.

2. **Resolver D-RES-01** — Decidir el schema de `reservations` antes de empezar Wave 2A. Es la decisión técnica con mayor impacto en la estabilidad futura.

3. **Lanzar Wave 1 en paralelo** — `roles-permisos` y `auditoria` son de bajo riesgo y pueden implementarse rápido. `configuracion-club + pistas` en un único change.

4. **Siguiente `/opsx:propose`** — Recomendado: `configuracion-club` (incluye `pistas`) como primer change de Wave 1.
