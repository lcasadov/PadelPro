## Context

El backend de `reservas` está desplegado y estable; la API del jugador (disponibilidad, crear, listar, detalle, cancelar) está documentada en `docs/openapi.yaml`. El error shape real es **`{ code, message, errors[] }`** (`errors` = array de `FieldError`), no `{error, message, details}`. El frontend (React 18 + Vite + TS) ya tiene `AuthContext` (token, role, mustChangePassword — **sin id numérico de usuario**), guards `PrivateRoute`/`AdminRoute`, un patrón de services axios donde cada service crea su `axios.create({ baseURL: '/api', withCredentials: true })` y recibe el `token` explícito por llamada (`authApi`, `usuariosApi`, `adminUsuariosApi`; `usuariosApi.getMeApi(token)` devuelve `UsuarioMe` con `id: number`), y suite de tests con vitest + testing-library + MSW. La UX está definida en `docs/ux/README.md` y `docs/ux/flujos.md` con mockups aplicables (03, 04, 05, 13) y design tokens (cream/ink/lime, Bricolage).

Este change es **mayoritariamente la capa de interfaz web del jugador**, con un **delta aditivo de backend** imprescindible: el endpoint de disponibilidad modela **una sola pista** y devuelve tramos con `plazasLibres = max_participants − ocupación`. Un tramo con `plazasLibres < max_participants` ya tiene una reserva incompleta que **solo admite unirse** (capability `partidas`, fuera de alcance); intentar crear ahí daría 409 (RN-RES-01). Para que la UI de "crear" solo ofrezca franjas reservables sin replicar la regla en el cliente, el backend marca cada tramo con un flag `creable`.

## Goals / Non-Goals

**Goals:**
- Completar el journey del jugador desde la web: buscar disponibilidad → confirmar → ver en "mis reservas" → cancelar.
- Consumir la API existente sin recalcular reglas de negocio en el cliente (precio, plazos, límites vienen del backend).
- Manejo de errores por código HTTP/negocio con mensajes claros y accionables.
- TDD estricto: test de servicio/componente antes de implementar.

**Non-Goals:**
- Vistas admin de reservas (confirmación, calendario, tabla del club) — el admin confirma vía API/`PATCH` por ahora.
- Pago online Redsys y pantalla de pago confirmado.
- Unirse a partidas (tramos parciales) / invitar usuarios registrados como participantes.
- Cualquier cambio en el **cálculo** de disponibilidad, migraciones o el resto del contrato API (el único delta es el flag `creable`, aditivo).

## Decisions

### D1 — Alcance v1 = solo vistas de jugador
Se implementan únicamente las 4 páginas del jugador (disponibilidad, confirmar, mis reservas, detalle). Las vistas admin quedan para un change posterior; hoy el admin confirma reservas CASH vía `PATCH /api/admin/reservas/{id}/estado`.
**Rationale (RN-AUTH-01/02):** los permisos de lectura/cancelación del jugador ya están garantizados por el backend (owner/participante); la UI solo expone lo que el usuario puede ver. Reduce alcance y acelera la entrega del journey central. *Alternativa descartada:* incluir vista admin mínima — añade tests y superficie sin bloquear el journey del jugador (el admin puede confirmar por API en la demo).

### D2 — Idempotency-Key generada en el cliente por intento
`crypto.randomUUID()` genera la key al entrar en "Confirmar reserva". Se **reutiliza** en reintentos de red del mismo `POST`; se **regenera** si el usuario cambia datos del formulario (fecha/hora/duración/participantes).
**Rationale (RN-RES-05):** el backend deduplica por `Idempotency-Key`; reutilizarla en reintentos evita reservas duplicadas por doble click o timeout, y regenerarla al cambiar datos evita que una key "vieja" devuelva una reserva con datos distintos a los mostrados. *Alternativa descartada:* key por sesión — colapsaría intentos legítimamente distintos.

### D3 — Selección de duración alineada al endpoint de disponibilidad
El endpoint devuelve tramos `{ horaInicio, duracionMinutos, plazasLibres, creable }`. La UI muestra los tramos tal cual los devuelve el backend (mockup 03); el usuario **elige un tramo ya resuelto** (hora + duración), no una duración abstracta previa. En "Confirmar" esos valores viajan como `startTime` + `durationMinutes`.
**Rationale (RN-RES-01/03):** alinear la semántica de la UI con la del backend evita construir combinaciones hora×duración inválidas en el cliente y deja la validación de solapamiento y precio donde corresponde (backend). *Alternativa descartada:* selector de duración previo a ver tramos — obligaría a filtrar/derivar en cliente y a re-consultar, duplicando lógica del backend. *Nota:* la granularidad actual del backend es de 60 min fijos, así que en v1 los tramos ofrecidos son de 60 min.

### D7 — Tramos reservables marcados por el backend (flag `creable`)
El backend añade `creable: boolean` a cada `TramoDisponible` (`true` sii `plazasLibres == max_participants`, equivalente a ocupación 0). La UI ofrece la acción "Reservar" **solo en tramos `creable`**. Los parciales (unibles) **se muestran deshabilitados con etiqueta "No disponible"** (no se ocultan): aparecen en la parrilla pero sin acción de crear, lo que evita huecos inexplicables sin revelar que es una partida unible. La UI trata `creable` ausente/undefined como `false` (fail-safe). Se decidió resolverlo con este flag en vez de exponer `max_participants` o un endpoint de config.
**Rationale (RN-RES-01/02):** solo un tramo sin ocupación puede crear una reserva; los parciales exigen `POST /unirse` (fuera de alcance). Un flag calculado en backend mantiene la regla de negocio fuera del cliente (RN-RES-03 en espíritu) y es a prueba de cambios de umbral futuros. *Alternativas descartadas:* (a) `maxParticipants` top-level → mete la comparación de regla en el cliente; (b) `GET /api/config` → endpoint nuevo, más superficie y decisiones de auth/cache; (c) filtrar en cliente por el máximo observado → frágil.

### D8 — Identidad del usuario cacheada en `AuthContext`
`AuthContext` hoy no expone el id numérico del usuario, pero `ReservaResponse.ownerId` es un `int` y no hay flag "es mía". Se añade `userId` a la sesión, cargado una vez de forma lazy vía `usuariosApi.getMeApi(token)` al entrar en el área de reservas, y se compara `me.id == ownerId` para decidir la visibilidad del botón cancelar.
**Rationale (RN-AUTH-02):** cancelar es exclusivo del owner; la UI necesita saber quién soy. Cachear en contexto es reutilizable (Mis reservas puede distinguir owner vs participante) y evita un request por página. *Alternativas descartadas:* (a) `getMeApi` por página → request extra por visita, no reutilizable; (b) decodificar el JWT en cliente → acopla el frontend al formato del token y exige verificar que el claim lleva el id numérico.

### D4 — Participantes adicionales solo `externalName` en v1
Se capturan participantes adicionales como texto libre (`externalName`, `externalPhone?` opcional), respetando el límite del backend (422 `PARTICIPANTS_LIMIT_EXCEEDED`).
**Rationale (RN-RES-02):** el máximo de participantes lo impone el backend; la UI no lo cablea, solo muestra el error si se excede. Invitar usuarios registrados pertenece a `partidas`. *Alternativa descartada:* buscador de usuarios registrados — fuera de alcance de esta capability.

### D5 — Refresco de disponibilidad tras 409 = botón manual
Ante un 409 `CONFLICT` al confirmar, se muestra el mensaje "la franja se acaba de ocupar" y un botón para volver a la búsqueda / refrescar disponibilidad. Sin polling automático.
**Rationale (RN-RES-01):** el conflicto es esporádico (carrera entre dos jugadores); un botón manual es más simple, predecible y barato que un polling que consumiría API sin garantía de mejora. *Alternativa descartada:* polling periódico — complejidad y carga innecesarias para un caso borde.

### D6 — `reservasApi.ts` centraliza el mapeo de errores
Un único service axios (patrón existente: `axios.create({ baseURL: '/api', withCredentials: true })`, `token` explícito por llamada) expone `getDisponibilidad(token, fecha)`, `crearReserva(token, payload, idempotencyKey)`, `getMisReservas(token)`, `getReserva(token, id)`, `cancelarReserva(token, id)`. Traduce el error shape real **`{ code, message, errors[] }`** a errores tipados por `code` para que las páginas rendericen mensajes específicos; los errores de campo se leen de `errors[]` (`FieldError`). Es un helper de mapeo **nuevo** (los services actuales no lo tienen).
**Rationale (RN-RGPD-03):** el backend distingue reserva ajena (403) de inexistente (404) — la UI respeta ambos sin inferir información. Centralizar el mapeo evita duplicar strings de error entre páginas. Códigos manejados: 409 `CONFLICT`, 400 `VALIDATION_ERROR`, 422 `PARTICIPANTS_LIMIT_EXCEEDED`/`INVALID_STATE_TRANSITION`/`CANCELLATION_DEADLINE_PASSED`, 403/404 en detalle y cancelación.

## Risks / Trade-offs

- **Carrera de reserva (409 al confirmar)** → Mitigación: mensaje claro + botón de refresco (D5); la barrera anti-solapamiento vive en el backend (RN-RES-01), la UI solo reacciona.
- **Reserva duplicada por reintento** → Mitigación: `Idempotency-Key` estable por intento (D2) reutilizada en reintentos de red.
- **Desalineación UI↔API en hora/duración** → Mitigación: la UI consume tramos ya resueltos del backend (D3), sin construir combinaciones en cliente.
- **Cancelación fuera de plazo** → Mitigación: no se calcula el plazo en cliente; se intenta el `DELETE` y se muestra el 422 `CANCELLATION_DEADLINE_PASSED` con mensaje de "no aplica reembolso" (RN-RES-04). *Nota:* la cancelación web es `DELETE`→204 directo, sin OTP (el OTP `CANCELLATION_CONFIRM` es una notificación Telegram, no una puerta web).
- **Reserva ajena vs inexistente** → Mitigación: la UI maneja tanto 403 (ajena, RN-RGPD-03) como 404 (inexistente/borrada) en `GET`/`DELETE` `/reservas/{id}` sin exponer datos; nunca loguear payloads sensibles (RN-RGPD-04).
- **Dependencia backend↔frontend** → Mitigación: la tarea de backend (`creable`) precede a las de frontend que lo consumen; si el backend no estuviera desplegado, la UI degradaría mostrando todos los tramos como no-reservables (fail-safe), nunca ofreciendo crear en un tramo no marcado.

## Migration Plan

Change aditivo en dos capas: (1) backend — nuevo campo `creable` en `TramoDisponible` + `openapi.yaml`, sin migraciones de BD ni cambio en el cálculo existente, retrocompatible (clientes viejos ignoran el campo); (2) frontend — nuevas rutas privadas, páginas y `userId` en `AuthContext`, sin tocar contratos existentes. Orden de despliegue: backend antes que frontend (el frontend consume `creable`). Rollback = revertir el commit/branch; no hay estado persistido ni migraciones que deshacer.

## Open Questions

- Ninguna bloqueante. Decisiones confirmadas con el usuario: D1 = solo vistas de jugador; tramos parciales = ocultos (solo `creable`); flag `creable` por tramo en backend; `userId` cacheado en `AuthContext`; un único change ampliado (frontend + delta backend). D2, D4, D5 según lo propuesto.
