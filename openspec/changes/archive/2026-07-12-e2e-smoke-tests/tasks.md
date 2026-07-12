## 1. Scaffolding Playwright

- [x] 1.1 Crear `e2e/package.json` con `@playwright/test` y scripts (`test`, `test:headed`, `report`)
- [x] 1.2 Crear `e2e/playwright.config.ts`: `baseURL` configurable (`E2E_BASE_URL`), Chromium, `retries` en CI, `trace: on-first-retry`, `ignoreHTTPSErrors`
- [x] 1.3 Crear `e2e/.gitignore` (node_modules, test-results, playwright-report)

## 2. Smoke del journey crítico

- [x] 2.1 Crear `e2e/tests/crear-reserva.smoke.spec.ts`: login admin → navegar a disponibilidad por UI → elegir franja (mañana) → confirmar → verificar confirmación
- [x] 2.2 Aserciones tolerantes: elige franja de la UI (no IDs); mensaje claro si no hay disponibilidad; reserva para mañana (backend rechaza pasado)
- [x] 2.3 Sin MSW ni stubs: peticiones al backend real (verificado en local: el smoke cazó VALIDATION_ERROR real de fecha pasada antes de corregir)

## 3. Integración en CI

- [x] 3.1 Añadir job `e2e` a `.github/workflows/ci.yml` (needs: build-and-test; corre en push y PR); `deploy` ahora depende de `[build-and-test, e2e]`
- [x] 3.2 Pasos: `docker compose up -d --build --wait` con env de prueba (JWT/ENCRYPTION/ADMIN + `MANAGEMENT_HEALTH_MAIL_ENABLED=false`) → `npm ci` → `playwright install --with-deps chromium` → `playwright test`
- [x] 3.3 Publicar `playwright-report` como artefacto `if: failure()`, logs del backend si falla, y `docker compose down -v` en `always()`

## 4. Documentación

- [x] 4.1 `e2e/README.md` con ejecución local del E2E (levantar stack + `npx playwright test`) y notas (seed, fecha futura, navegación por UI, secure-context)

## 5. Validación y cierre

- [x] 5.1 Verificado en local: smoke en VERDE contra el stack real (1 passed) tras corregir navegación (routing cliente) y fecha futura
- [x] 5.2 `openspec validate e2e-smoke-tests` en verde
- [x] 5.3 Auto-revisión + PR

## Notas de implementación
- Se añadió `MANAGEMENT_HEALTH_MAIL_ENABLED` (default `true`) al backend en `docker-compose.yml`: sin SMTP real el health de correo marcaba `/actuator/health` DOWN y bloqueaba el arranque del frontend (`depends_on: healthy`). En prod queda `true` (sin cambio); E2E/CI lo pone `false`.
