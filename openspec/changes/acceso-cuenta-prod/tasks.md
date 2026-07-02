# Tasks — acceso-cuenta-prod (TDD)

> Ciclo por unidad: **🔴 Red** (test que falla) → **🟢 Green** (implementación mínima) → **♻️ Refactor**.
> Backend contra PostgreSQL real (`:5433`, base `PostgresIntegrationTest`); frontend con vitest (test de componente primero). Fuente autoritativa: `docs/TESTING-STRATEGY.md`.

## 0. Gestión y arranque (orquestador)

- [ ] 0.1 Crear/identificar Issue en GitHub y obtener su número
- [ ] 0.2 Crear rama `feat/<id>-acceso-cuenta-prod` desde `develop` y push
- [ ] 0.3 Mover el item del Project v2 a "In Progress"
- [ ] 0.4 Releer `docs/TESTING-STRATEGY.md` y fijar convenciones (naming, fixtures, perfiles)

## 1. Esquema base (habilitador de los ciclos)

- [x] 1.1 Migración: `users.must_change_password BOOLEAN NOT NULL DEFAULT false` (con test de migración que verifica la columna y el default)

## 2. Seed del primer admin (auth-local) — TDD

- [x] 2.1 🔴 Test: con BD sin ADMIN y `ADMIN_EMAIL`/`ADMIN_PASSWORD` definidos, al arrancar se crea un ADMIN `ACTIVE` con hash BCrypt
- [x] 2.2 🟢 Implementar el `ApplicationRunner` mínimo que hace pasar 2.1
- [x] 2.3 🔴 Test: si ya existe un ADMIN, el arranque no crea otro (idempotente) → 🟢 ajustar
- [x] 2.4 🔴 Test: sin variables de entorno, el arranque no falla y no crea admin → 🟢 ajustar
- [x] 2.5 ♻️ Refactor (extraer servicio/política) manteniendo verde; documentar `ADMIN_EMAIL`/`ADMIN_PASSWORD` en `.env.example` y runbook

## 3. Acceso provisional de 2 días + bloqueo (auth-local) — TDD

- [x] 3.1 🔴 Test: usuario `PENDING` con `created_at` < 48 h → login 200
- [x] 3.2 🟢 Modificar la regla de login para permitir PENDING dentro de la ventana (mínimo) → verde
- [x] 3.3 🔴 Test: `PENDING` con `created_at` > 48 h → 403 `ACCOUNT_NOT_ACTIVE` → 🟢 ajustar
- [x] 3.4 🔴 Test: `INACTIVE` → 403 siempre (sin gracia) y `ACTIVE` → 200 → 🟢 ajustar
- [x] 3.5 ♻️ Refactor de la política de acceso (objeto de dominio testeado en aislamiento)

## 4. Cambio de contraseña forzado (auth-local) — TDD

- [x] 4.1 🔴 Test: el login de una cuenta con `must_change_password=true` señala el cambio requerido en la respuesta
- [x] 4.2 🟢 Exponer el flag en la respuesta de login (mínimo) → verde
- [x] 4.3 🔴 Test: endpoint de cambio de contraseña propio valida política, guarda BCrypt y pone `must_change_password=false`
- [x] 4.4 🟢 Implementar/extender el endpoint (`/usuarios/me` o dedicado) → verde
- [x] 4.5 🔴 Test de seguridad: contraseña que no cumple política → 400; no se loguea → 🟢 / ♻️

## 5. Reset de contraseña por el admin (usuarios) — TDD

- [x] 5.1 🔴 Test: ADMIN resetea → 200, devuelve temporal en claro una vez, persiste BCrypt y marca `must_change_password=true`
- [x] 5.2 🟢 Implementar `PATCH /api/admin/usuarios/{id}/reset-password` mínimo → verde
- [x] 5.3 🔴 Test: login posterior con la temporal funciona y exige cambio → 🟢 ajustar
- [x] 5.4 🔴 Test: USER → 403; la temporal no aparece en logs; respeta protección de identidad ADMIN (RN-AUTH-05) → 🟢
- [x] 5.5 ♻️ Refactor (generador de contraseña temporal aislado y testeado)

## 6. Panel admin de usuarios (frontend, usuarios) — test-first de componente

- [ ] 6.1 🔴 Test: `AdminRoute` redirige a un USER y deja pasar a un ADMIN → 🟢 implementar guard
- [ ] 6.2 🔴 Test: `adminUsuariosApi` (mock) — listar con filtro, aprobar, activar/desactivar, reset → 🟢 implementar servicio
- [ ] 6.3 🔴 Test de componente: la página `/admin/usuarios` lista, filtra por estado y dispara aprobar → 🟢 implementar
- [ ] 6.4 🔴 Test: la acción de reset muestra la temporal devuelta una sola vez (con aviso) → 🟢 implementar
- [ ] 6.5 ♻️ Refactor + entrada de nav condicionada a ADMIN

## 7. UX de acceso (frontend, auth-local) — test-first de componente

- [ ] 7.1 🔴 Test: el botón "¿Olvidaste la contraseña?" navega a `/forgot-password` → 🟢 enlazar + pantalla "contacta con el administrador"
- [ ] 7.2 🔴 Test: el login mapea `403 ACCOUNT_NOT_ACTIVE` → mensaje de cuenta pendiente/bloqueada y `401` → credenciales inválidas → 🟢 implementar
- [ ] 7.3 🔴 Test: tras registro correcto se muestra la confirmación "pendiente de aprobación; acceso provisional 2 días" → 🟢 implementar
- [ ] 7.4 🔴 Test: si el login indica `must_change_password`, se fuerza la pantalla de cambio antes de entrar → 🟢 implementar
- [ ] 7.5 ♻️ Refactor de los componentes/estados de error

## 8. API spec

- [ ] 8.1 Actualizar `docs/openapi.yaml`: endpoint de reset admin (temporal de un solo uso), flag de cambio requerido en login, códigos

## 9. QA (verificación adversarial, sobre código ya cubierto por TDD)

- [ ] 9.1 `test-runner`: suite completa backend + frontend en verde (Postgres real :5433) y cobertura dentro del umbral JaCoCo
- [ ] 9.2 `verification-specialist`: probes (reset solo ADMIN, temporal fuera de logs, gracia 48h en bordes, guard de rol, cambio forzado)
- [ ] 9.3 `reality-checker`: journey end-to-end — seed admin → login admin → registro usuario (PENDING) → acceso provisional → admin aprueba en panel → reset admin → login con temporal → cambio forzado → uso normal

## 10. Cierre

- [ ] 10.1 PR con `Closes #<id>` y CI en verde
- [ ] 10.2 Merge → deploy automático; en el `.env` del EC2 añadir `ADMIN_EMAIL`/`ADMIN_PASSWORD`; verificar login admin y panel en vivo
