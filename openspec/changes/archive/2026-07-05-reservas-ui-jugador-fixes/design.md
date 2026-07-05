## Context

El change `reservas-ui-jugador` entregó las 4 páginas del jugador (Disponibilidad, Confirmar, Mis Reservas, Detalle) y el servicio `reservasApi.ts`. En uso real (2026-07-05) el flujo no se completa y faltan funcionalidades. La exploración del código confirma que **el backend ya soporta casi todo lo que falta**:

- Duraciones {60,90,120,150,180} con `startTime` en minutos 00/30 (`CrearReservaService.ALLOWED_DURATIONS`, refuerzo por CHECK en `V7`). RN-RES: franjas de media hora.
- XOR de participante `userId` (registrado) vs `externalName`/`externalPhone` (externo), owner en slot 1, límite `max_participants_per_pista` (422 `PARTICIPANTS_LIMIT_EXCEEDED`).
- Cancelación `DELETE /api/reservas/{id}` con política de plazo (RN-RES-04) y refund del pago si estaba PAID.
- Al crear, el backend congela el precio (RN-RES-03) y crea un `Payment` en `PENDING`.

Los gaps son por tanto **de frontend**, con un delta de backend acotado: el endpoint de búsqueda de usuarios para el buscador de socios. El pago online Redsys (`pagos-redsys`) y el refresh de sesión (`auth-session-refresh`) son piezas grandes que se separan a changes propios.

**Bug de creación — causa raíz confirmada (E2E 2026-07-05):** el backend crea la reserva (HTTP 201). Reproducido contra el stack real: `system_config` presente (500 descartado), el módulo reservas no envía email (SMTP descartado), y una franja futura con token válido → 201. El error que ve el usuario es un **401 `AUTH_REQUIRED`** por access token caducado (15 min, solo en memoria, sin refresh). El contrato de error real es `{ error, message, timestamp, details? }` (el código va en `error`, no en `code`; la lista es `details`, no `fieldErrors`); el frontend `messageForError` lee `err.fieldErrors` (inexistente) y `codeFromStatus` no contempla 401 → `UNKNOWN` → mensaje genérico. La corrección es de frontend: manejar el 401 (D2) y alinear el mapeo al contrato real.

## Goals / Non-Goals

**Goals:**
- Que una reserva válida se cree con éxito y que cada error muestre un mensaje específico (RN-RES-03/05).
- Selector de duración 60/90/120 (backend ya acepta 150/180) en franjas de media hora.
- Distinguir compañero registrado (`userId`) de invitado externo (`externalName`/`externalPhone`) en la UI, respetando el XOR del backend.
- Volver a Inicio desde cualquier página de reservas.
- "Mis Reservas" operativa: cancelar, ver estado de pago y elegir método de pago (diferido / ahora).
- Entregar el núcleo (todo salvo "pagar ahora") **sin bloquear** con Redsys.

**Non-Goals:**
- Construir la pasarela Redsys completa (fase-2 `pagos-redsys`).
- Vistas admin de reservas; unirse a partidas; notificaciones; rediseño visual.
- Cambiar la lógica de dominio de creación/cancelación (ya correcta).

## Decisions

### D1 — Este change entrega el núcleo; el pago online Redsys va a un change propio
Decidido en revisión: **este change (fase-1)** cubre bug de creación, selección de duración, tipos de participante, navegación Home y "Mis Reservas" con cancelar + estado de pago + método de pago con **"pago diferido" funcional**. La acción **"pagar ahora" queda como punto de entrada deshabilitado/informativo** ("disponible próximamente"). El **pago online Redsys se traslada a un change propio `pagos-redsys`** (fase-2), que se abordará cuando haya credenciales del comercio. Motiva RN-RES-03 (precio congelado por backend) y que Redsys no existe aún. *Alternativa descartada:* un único entregable con Redsys — bloquearía el valor inmediato (cancelar/duración/compañero) tras la integración de pasarela y credenciales.

### D2 — Corrección del bug de creación: manejar el 401 de sesión caducada + alinear contrato de error
**Causa raíz confirmada en vivo (E2E 2026-07-05):** el backend crea la reserva (HTTP 201); el error que ve el usuario es un **401 `AUTH_REQUIRED`** por access token caducado (TTL 900s, solo en memoria, sin refresh — ver `AuthContext.tsx`). `codeFromStatus` no contempla 401 → `UNKNOWN` → `messageForError` rama `default` → mensaje genérico. Decisión: en el flujo de reserva, **detectar el 401 y mostrar "sesión caducada" con redirección a login** (que el usuario se re-autentique y reanude), y **reescribir `messageForError`** para leer el contrato real (`error` como código, `details[0]` como detalle) en lugar de `fieldErrors`, de modo que 400/409/422 muestren mensaje específico. El backend **no** necesita cambios de dominio (verificado). Motiva RN-RES-05 (feedback claro). *Alternativa descartada:* silenciar/reintentar el 401 aquí con la cookie de refresh — es la solución correcta pero global; se separa a `auth-session-refresh` (ver D7) para no acoplar el arreglo de sesión al change de reservas.

### D7 — El refresh silencioso de sesión es un change propio, no de reservas
El defecto de fondo —access token de 15 min sin renovación— rompe **cualquier** escritura de la app tras 15 min; reservar es solo donde se detectó. La solución (interceptor axios que ante 401 llama a `POST /api/auth/refresh` con la cookie `refresh_token` de 7 días y reintenta la petición original) pertenece a `auth-local` y beneficia a toda la app. Decisión: **abordarlo en un change propio `auth-session-refresh`**; en *este* change solo el manejo local del 401 (D2). *Alternativa descartada:* meter el interceptor aquí — mezclaría un arreglo global de auth con una feature de reservas y complicaría la revisión y el archivado de specs.

### D3 — Selector de duración (60/90/120) en la página de confirmación, filtrando por disponibilidad
Decidido en revisión: la UI ofrece **60/90/120 min** (el backend acepta hasta 180, pero no se exponen 150/180 en esta fase). La duración pasa de valor fijo propagado por query-string a un selector con opciones válidas para la franja (no solaparse ni exceder el horario). El frontend deriva las opciones a partir de la disponibilidad ya consultada; si resulta insuficiente, se apoya en el 409 `CONFLICT` del backend como red de seguridad. Default 60 min. Motiva la RN de franjas de media hora. *Alternativa descartada:* elegir duración en Disponibilidad — obliga a recalcular tramos por cada duración; es más simple elegirla en Confirmar donde ya se conoce la franja base.

### D4 — Participante: componente con conmutador "socio / externo"
Cada fila de participante ofrece un conmutador de tipo. "Socio" muestra un buscador que consulta el endpoint de usuarios (D5) y fija `userId`; "externo" muestra nombre + teléfono y fija `externalName`/`externalPhone`. El payload envía exactamente uno (XOR), acorde a la validación del backend. *Alternativa descartada:* un único input de texto que "adivine" si es socio — ambiguo y propenso a errores; el backend exige XOR explícito.

### D5 — Búsqueda de socios: buscador con endpoint mínimo de usuarios
Decidido en revisión: **buscador de socios**. Se añade `GET` de búsqueda en `usuarios` que devuelve solo `id` + nombre, autenticado, con término mínimo y resultados acotados en tamaño. *Alternativa descartada:* pedir el email exacto del socio y resolverlo en el POST de reserva — evita el endpoint pero empeora la UX y filtra existencia de emails.

### D6 — "Mis Reservas" con acciones inline
Las tarjetas pasan de simple `Link` al detalle a incluir acciones: cancelar (reutiliza `DELETE /api/reservas/{id}`), badge de estado de pago (ya disponible en `ReservaResponse.pago`), y un control de método de pago. "Pago diferido" no llama a ningún endpoint (cobro presencial confirmado por admin, comportamiento actual). "Pagar ahora" queda deshabilitado/informativo hasta que se entregue el change `pagos-redsys`. Se mantiene el detalle para la vista completa.

## Risks / Trade-offs

- [El manejo local del 401 (mensaje + redirect) no evita que el usuario pierda el trabajo en curso al re-loguearse] → Mitigación aceptable en este change (el arreglo bueno es el refresh silencioso de `auth-session-refresh`, D7); documentar que reservar debe completarse dentro de la ventana de sesión hasta que ese change entregue el refresh.
- [Filtrar duraciones válidas en cliente puede divergir de las reglas reales de solapamiento] → Mitigación: el 409 `CONFLICT` del backend sigue siendo la autoridad; el filtrado en cliente es solo UX, no regla de negocio.
- [El endpoint de búsqueda de usuarios podría exponer el directorio de socios] → Mitigación: autenticación obligatoria, resultados mínimos (id+nombre), límite/paginación, y término mínimo antes de consultar.
- [Ofrecer "pagar ahora" sin Redsys confunde al usuario] → Mitigación: la opción se muestra deshabilitada con texto "disponible próximamente"; solo se habilita al entregar el change `pagos-redsys`.
- [El change hermano `reservas-ui-jugador` aún no está archivado; sus requirements no están en el spec principal] → Mitigación: los deltas de este change usan `ADDED` (no `MODIFIED`) para no depender de requirements aún no fusionados; al archivar ambos, sincronizar orden.

## Migration Plan

- Sin migraciones de base de datos en Fase A. Fase B (Redsys) reutiliza las columnas de gateway ya presentes en `Payment`; sin migración nueva prevista.
- Despliegue Fase A: frontend + delta backend (mapeo de error, endpoint búsqueda) desplegables juntos; retrocompatibles (cambios aditivos). Rollback = revertir el frontend; el delta de backend es aditivo y no rompe consumidores existentes.
- El pago online Redsys se despliega de forma independiente en el change `pagos-redsys` cuando esté listo y con credenciales del comercio configuradas.

## Open Questions

Resueltas en revisión (2026-07-05):
- **Selector de socio** → buscador con endpoint de búsqueda de usuarios (D5).
- **Pago online Redsys** → change propio `pagos-redsys`; aquí solo el punto de entrada deshabilitado (D1).
- **Duraciones en UI** → 60/90/120 (D3).
