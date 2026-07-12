## Context

`e2e/` usa Playwright contra el stack docker-compose real. El único spec (`crear-reserva.smoke.spec.ts`) establece el patrón: login con el admin de bootstrap, navegación por UI (nunca `page.goto` en rutas privadas, porque el access token vive en memoria y una recarga cierra sesión — RN-AUTH-09), reserva para *mañana*, y reintento sobre `409` para ser re-ejecutable sobre BD reutilizada. Este change reutiliza ese patrón para más journeys.

## Goals / Non-Goals

**Goals:**
- Cubrir E2E los flujos reales de mayor valor, de forma determinista y re-ejecutable.
- Reusar el estilo/patrón del smoke existente; entrar en el job `e2e` de CI sin tocar el workflow.

**Non-Goals:**
- Acciones que requieran múltiples usuarios reales sembrados (unirse a partida ajena), TPV Redsys en vivo o bot Telegram real.
- Sustituir los tests de componente.

## Decisions

### D1 — Un spec por journey, autocontenido
Cada journey en su propio `*.spec.ts`, que crea sus propios datos (p. ej. `mis-reservas-cancelar` crea la reserva que luego cancela). Evita dependencias de orden entre specs y mantiene la re-ejecutabilidad.

### D2 — Reutilizar el patrón de reintento sobre 409
Los journeys que crean reservas reutilizan el bucle "probar franjas sucesivas hasta confirmar" del smoke, para funcionar sobre una BD ya usada (local) igual que sobre BD fresca (CI).

### D3 — Alcance reducido documentado donde no es determinista
`partidas-listado` cubre que la página carga (listado/estado vacío) en vez de "unirse", porque unirse exige una partida de otro usuario. Se documenta en el propio spec. Preferible un test verde y honesto a un `skip` sin explicación o un test frágil.

### D4 — Descarga CSV del dashboard
`dashboard-admin` verifica el export con `page.waitForEvent('download')` (Playwright captura la descarga del blob) en vez de inspeccionar el sistema de ficheros.

## Risks / Trade-offs

- **[Acumulación de reservas en BD local]** → Mitigación: patrón de reintento; los specs que pueden, limpian tras de sí (cancelar). En CI la BD es fresca por run.
- **[Flakiness E2E]** → Mitigación: auto-waiting de Playwright, aserciones sobre roles/textos estables, `retries` en CI ya configurados.
- **[Tiempo de CI]** → Mitigación: siguen siendo pocos specs, un solo navegador (Chromium), tras `build-and-test`.

## Migration Plan

1. Añadir los specs en `e2e/tests/`.
2. Verificar en verde contra el stack real (local) y en el job `e2e` de CI.
3. Rollback: eliminar los specs (no afecta a producto).
