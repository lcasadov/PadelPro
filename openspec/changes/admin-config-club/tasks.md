# Tareas — admin-config-club

## 1. Backend (verificación, sin código nuevo esperado)

- [ ] 1.1 Verificar que `GET /api/admin/sistema/config` devuelve todos los campos que la UI necesita (precio, estado pista, aforo, plazo cancelación, pasarela, secretos enmascarados, telegram group id) y que `PATCH` aplica actualización parcial. Corregir en el DTO/servicio existente solo si aparece un gap.
- [ ] 1.2 Confirmar el enmascarado de secretos en la respuesta (nunca en claro) y el 403 para USER.

## 2. Frontend — servicio y ruta

- [ ] 2.1 `src/services/configApi.ts`: `getSystemConfig(token)` y `updateSystemConfig(token, partial)` sobre el `httpClient` compartido; mapear la forma real de `SystemConfigResponse`; construir el body PATCH solo con campos modificados (secretos solo si se teclean).
- [ ] 2.2 Ruta `/admin/config` en `App.tsx` protegida por rol ADMIN (mismo guard que `/admin/usuarios`).
- [ ] 2.3 Enlace "Configuración" en `HomePage` (sección admin).

## 3. Frontend — pantalla

- [ ] 3.1 `ConfiguracionPage`: form con nombre/descripción del club, **precio/hora**, **estado pista** (ACTIVA/MANTENIMIENTO con aviso RN-RES-04), aforo, plazo cancelación, pasarela, y **Telegram** (bot token, webhook secret, group id) con campos de secreto enmascarados.
- [ ] 3.2 Validación cliente (precio > 0, aforo ≥ 1, plazo ≥ 0); estados de carga/guardado/error; confirmación al guardar.

## 4. Testing

- [ ] 4.1 Componente (Vitest + MSW): carga inicial, guardar precio, dejar secreto en blanco NO lo envía, teclear secreto SÍ lo envía, USER denegado, valor inválido no deja guardar.
- [ ] 4.2 E2E (`admin-config.spec.ts`): ADMIN entra en `/admin/config`, cambia el precio, guarda, recarga y el valor persiste.

## 5. QA y cierre

- [ ] 5.1 Backend `mvn -DskipITs test` verde; Frontend `tsc`+`lint`+`test`+`build` verde; E2E verde contra el stack real.
- [ ] 5.2 PR y merge tras CI verde; archivar el change.
