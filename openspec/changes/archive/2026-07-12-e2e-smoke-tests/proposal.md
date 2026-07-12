## Why

Todos los tests de frontend corren en **jsdom con HTTP mockeado (MSW)** y el backend se prueba con unit + IT. **Ningún test ejecuta un navegador real contra el stack real.** Ese hueco dejó pasar a producción el bug #201 (`crypto.randomUUID` / *secure context*, en el journey de crear reserva): los tests con mocks pasaban en verde mientras el flujo real fallaba. Necesitamos una red de seguridad end-to-end que ejercite el journey crítico en un navegador de verdad.

## What Changes

- Se añade **Playwright** al proyecto (nuevo directorio `e2e/` con su propio `package.json` y `playwright.config.ts`).
- Se añade un **smoke E2E del journey crítico**: iniciar sesión → buscar disponibilidad → confirmar reserva → ver confirmación. Sin mocks: contra frontend build + backend + postgres reales.
- Se añade un **job `e2e` en `.github/workflows/ci.yml`** que levanta el stack con `docker compose`, espera a los healthchecks, ejecuta Playwright (Chromium) y publica traza/artefactos en caso de fallo. Corre en `pull_request` y `push`, después de `build-and-test`.
- Se documenta la ejecución local del E2E en el README/runbook.

## Capabilities

### New Capabilities
<!-- Ninguna capability de producto nueva; es testing transversal. -->

### Modified Capabilities
- `ci-cd-deploy`: se añade el requisito de una **suite E2E smoke** en el pipeline que ejercita el journey crítico en un navegador real contra el stack completo, como gate previo al despliegue.

## Impact

- **Testing / CI:** nuevo `e2e/` (Playwright), nuevo job `e2e` en el workflow. Aumenta el tiempo de CI (~unos minutos: build de imágenes + arranque + navegador).
- **Sin cambios de código de negocio** ni de esquema.
- **Dependencias (dev):** `@playwright/test` y el navegador Chromium (instalado en CI con `playwright install --with-deps`).
- **Fase del producto:** fase-1 (endurecimiento de calidad, transversal).

## Fuera de alcance

- No se cubren todos los journeys: solo el smoke crítico (login → crear reserva). Ampliar cobertura E2E (cancelar, unirse a partida, pagos) es follow-up.
- No se ejecuta el E2E contra el EC2 de producción (secure-context real): eso sería un *post-deploy smoke* aparte contra `https://<PUBLIC_HOST>`, follow-up sobre el change `tls-https-ec2`.
- No se sustituyen los tests unitarios/componente existentes (MSW/vitest): el E2E los complementa, no los reemplaza.
