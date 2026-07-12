## Context

El frontend se prueba con vitest + jsdom + MSW (mocks de red) y el backend con unit + IT. No hay ningún test que arranque un navegador real contra el stack real. El bug #201 (crear reserva rompía en prod por *secure context*) pasó todos los tests porque los mocks ocultaban el comportamiento real. Este change añade la capa E2E que faltaba.

El stack ya es *dockerizable* (`docker-compose.yml`): `db` (postgres), `backend` (:8080) y `frontend` (:5173, build estático por `vite preview`). Un job de CI puede levantarlo y ejercitarlo.

## Goals / Non-Goals

**Goals:**
- Ejercitar el journey crítico login → crear reserva en Chromium real contra el stack completo.
- Correr en CI como gate previo al deploy, con evidencia (traza) al fallar.
- Ejecutable también en local con un comando.

**Non-Goals:**
- Cobertura E2E exhaustiva (solo el smoke crítico).
- Reproducir el *secure-context* de prod HTTP (localhost siempre es secure context; eso es un post-deploy smoke sobre `tls-https-ec2`).
- Sustituir los tests de componente existentes.

## Decisions

### D1 — Playwright (frente a Cypress)
Se elige **Playwright** por: instalación de navegadores reproducible (`playwright install --with-deps`), API de espera robusta (auto-waiting) que reduce *flakiness*, trazas ricas para depurar fallos de CI, y buen soporte headless en Ubuntu runners. Cypress es válido pero Playwright integra mejor el multi-navegador y las trazas sin plugins.

### D2 — Directorio `e2e/` separado con su propio `package.json`
El E2E vive en `e2e/` (no dentro de `frontend/`) porque prueba el **sistema**, no el paquete frontend, y no debe arrastrar Playwright a las dependencias del build del frontend ni mezclarse con vitest. Aísla dependencias y config.

### D3 — Target: stack docker-compose real, sin mocks (RN: causa de #201)
La suite apunta a `http://localhost:5173` (frontend build) que habla con el backend real. **Prohibido MSW / stubs**: el valor de esta capa es precisamente ejercitar la integración real que los mocks ocultaron. Motivado directamente por el patrón de fallo de #201.

### D4 — Seed de datos para el journey
Para poder loguear y reservar, el job de CI arranca el backend con `ADMIN_EMAIL`/`ADMIN_PASSWORD` (bootstrap de un ADMIN ACTIVE) y `JWT_SECRET`/`ENCRYPTION_KEY` de prueba. La configuración del club (`SystemConfig`) y la disponibilidad las provee el arranque estándar (Flyway). El test inicia sesión con ese usuario y recorre disponibilidad → confirmar. Si el journey exacto de disponibilidad requiere una franja concreta, el test la selecciona de la UI (no hardcodea IDs), tolerando el estado inicial.

### D5 — Robustez frente a flakiness
`playwright.config.ts` con `webServer` deshabilitado (el stack lo levanta CI), `retries: 1` en CI, `trace: 'on-first-retry'`, timeouts generosos para el arranque del backend. En local, un script `e2e:up` documentado levanta el stack antes de `npx playwright test`.

## Risks / Trade-offs

- **[El E2E es lento y puede ralentizar CI]** → Mitigación: solo Chromium, un único smoke, corre tras `build-and-test` (no en paralelo con cada unit test); cachés de npm/imagen donde aplique.
- **[Flakiness por timing del arranque del backend]** → Mitigación: esperar healthchecks del compose antes de lanzar Playwright; auto-waiting de Playwright; `retries: 1` en CI.
- **[El seed de datos inicial no permite reservar (sin disponibilidad/config)]** → Mitigación: el backend arranca con la config por defecto (Flyway `SystemConfig`); el test elige la primera franja disponible de la UI y falla con traza clara si no hay ninguna, señal de un problema de seed a corregir.
- **[localhost es secure context → el E2E NO reproduce #201 exacto]** → Mitigación: se documenta explícitamente; el post-deploy smoke contra HTTPS real (follow-up de `tls-https-ec2`) cubre ese ángulo. Aun así, este E2E sí habría cazado cualquier ruptura del journey por contrato/UI.

## Migration Plan

1. Añadir `e2e/` (Playwright) y el job `e2e` al workflow.
2. En CI: `docker compose up -d --build` con env de prueba → esperar health → `npx playwright test` → subir traza si falla → `docker compose down`.
3. Rollback: retirar el job `e2e` (no afecta a build/deploy existentes).
