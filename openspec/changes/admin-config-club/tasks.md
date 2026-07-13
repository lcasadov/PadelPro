# Tareas — admin-config-club

## 1. Backend (verificación, sin código nuevo esperado)

- [x] 1.1 Verificado: `GET /api/admin/sistema/config` (`SystemConfigResponse`) devuelve precio, estado pista, aforo, plazo, pasarela y **booleanos** `telegramBotConfigured`/`redsysConfigured` (los secretos no se leen). `PATCH` obligatorios: clubName/pistaState/paymentGateway/maxParticipants; opcionales conservan valor. Sin código nuevo.
- [x] 1.2 Confirmado: secretos nunca en claro (solo booleanos) y `@PreAuthorize("hasRole('ADMIN')")` → 403 USER.

## 2. Frontend — servicio y ruta

- [x] 2.1 `src/services/configApi.ts`: `getSystemConfig` + `updateSystemConfig` sobre `httpClient`; tipos `SystemConfig`/`UpdateSystemConfig`; body con obligatorios siempre + secretos solo si se teclean.
- [x] 2.2 Ruta `/admin/config` en `App.tsx` bajo `AdminRoute` (guard de rol ADMIN existente).
- [x] 2.3 Enlace "Configuración" en `HomePage` (sección admin).

## 3. Frontend — pantalla

- [x] 3.1 `ConfiguracionPage`: form con club, **precio/hora**, **estado pista** (con aviso RN-RES-04), aforo, plazo, pasarela (+ Redsys condicional) y **Telegram** (bot token, webhook secret, group id); secretos con placeholder, no se leen.
- [x] 3.2 Validación cliente (precio > 0, aforo ≥ 1, plazo ≥ 0, Redsys exige credenciales); estados carga/guardado/error; confirmación.

## 4. Testing

- [x] 4.1 Componente (Vitest + MSW) — 6/6: carga, guardar precio, blanco NO envía secreto, teclear SÍ lo envía, inválido no guarda, error de carga.
- [x] 4.2 E2E (`admin-config.spec.ts`) verde: ADMIN entra en `/admin/config`, cambia el precio a 17.5, guarda, re-login y el valor persiste.

## 5. QA y cierre

- [x] 5.1 Frontend `tsc`+`lint`+`test`(234)+`build` verde; E2E `admin-config` verde contra el stack real. Backend sin cambios (no aplica `mvn`).
- [ ] 5.2 PR y merge tras CI verde; archivar el change.
