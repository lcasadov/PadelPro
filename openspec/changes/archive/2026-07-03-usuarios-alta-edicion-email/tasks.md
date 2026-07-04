# Tasks — usuarios-alta-edicion-email (TDD)

> Ciclo por unidad: **🔴 Red** (test que falla) → **🟢 Green** (mínimo) → **♻️ Refactor**.
> Backend contra PostgreSQL real (`:5433`, base `PostgresIntegrationTest`); frontend con vitest (test de componente primero). Fuente: `docs/TESTING-STRATEGY.md`.
> ⚠️ Credenciales SMTP: solo en el `.env` del EC2 y placeholders en `.env.example`. NUNCA commitear las reales.

## 0. Gestión y arranque (orquestador)

- [x] 0.1 Issue = #178
- [x] 0.2 Rama `feat/178-usuarios-alta-edicion-email` creada desde `develop` y pusheada
- [x] 0.3 Issue #178 abierto (item del Project)

## 1. Infraestructura de email (notificaciones) — TDD

- [x] 1.1 Añadir `spring-boot-starter-mail` al `pom.xml`; `@EnableAsync` + executor
- [x] 1.2 🔴 Test: puerto `NotificationPort.sendWelcomeEmail(...)` — un fallo del adaptador SMTP no propaga excepción al llamador (se traga/loguea) → 🟢 implementar adaptador SMTP (`JavaMailSender`) tolerante a fallos
- [x] 1.3 🔴 Test: la contraseña temporal no aparece en logs al enviar/fallar → 🟢 asegurar
- [x] 1.4 Config SMTP por entorno (`MAIL_HOST/PORT/USERNAME/PASSWORD/FROM`); placeholders en `.env.example`; passthrough en `docker-compose.yml` (backend `environment`)
- [x] 1.5 ♻️ Refactor: plantilla de email de bienvenida (asunto + cuerpo con contraseña y bienvenida)

## 2. Backend — email al activar (usuarios + notificaciones) — TDD

- [x] 2.1 🔴 Test: al **aprobar** una cuenta PENDING NO se resetea la contraseña (conserva la del registro) y se invoca `sendWelcomeEmail` SIN contraseña → 🟢 implementar (enganchar en `approveUser`)
- [x] 2.2 🔴 Test: al **dar de alta** un usuario (ACTIVE) el sistema genera la contraseña (no la del request), `must_change_password=true`, y dispara el email CON la temporal → 🟢 implementar (ajustar `createUser`)
- [x] 2.3 🔴 Test: si el envío de email falla, la activación/alta se completa igualmente (200/201) → 🟢 verificar el `@Async`/try-catch
- [x] 2.4 ♻️ Refactor del disparo del email (bienvenida-con-pw en alta, bienvenida-sin-pw en aprobación — D3)

## 3. Frontend — alta de usuario (usuarios) — test-first

- [x] 3.1 🔴 Test: `adminUsuariosApi.crearUsuario` (mock) llama `POST /api/admin/usuarios` con nombre/email/rol → 🟢 implementar
- [x] 3.2 🔴 Test de componente: botón "Dar de alta" abre el formulario; enviar crea y refresca la lista → 🟢 implementar formulario de alta (sin campo contraseña)
- [x] 3.3 🔴 Test: alta con email duplicado muestra el error de conflicto → 🟢 manejar el 409
- [x] 3.4 ♻️ Refactor del formulario/estado

## 4. Frontend — edición de usuario (usuarios) — test-first

- [x] 4.1 🔴 Test: `adminUsuariosApi.editarUsuario` (mock) llama `PATCH /api/admin/usuarios/{id}` con nombre/email/teléfono (sin rol) → 🟢 implementar
- [x] 4.2 🔴 Test de componente: acción "Editar" precarga los datos, el formulario NO ofrece cambiar rol, guardar aplica el `PATCH` y refresca la lista → 🟢 implementar formulario de edición
- [x] 4.3 🔴 Test: edición con datos inválidos (email mal formado) muestra el error (400) → 🟢 manejar
- [x] 4.4 ♻️ Refactor
- [x] 4.5 (#179) 🔴 Fix del bug de validación de email real: backend `@NotBlank/@Email` + `@Valid` + handler `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` (IT reales POST/PATCH); frontend valida email en `UsuarioFormModal` (no dispara request) y sustituye el test circular de `adminUsuariosApi.crud.test.ts` por `UsuarioFormModal.test.tsx`

## 5. API spec

- [x] 5.1 Revisar/actualizar `docs/openapi.yaml`: alta (`POST /api/admin/usuarios`) sin contraseña de entrada (generada por el sistema) y nota del email de bienvenida al activar

## 6. QA

- [x] 6.1 `test-runner`: suite backend + frontend en verde (Postgres real :5433) y cobertura dentro del umbral JaCoCo
- [x] 6.2 `verification-specialist`: probes (email no bloquea activación, contraseña fuera de logs, alta/edición solo ADMIN, conflicto/validación)
- [x] 6.3 `reality-checker`: journey — admin da de alta usuario → llega email de bienvenida (Ethereal) con contraseña → login con esa contraseña → cambio forzado; admin edita un usuario; admin aprueba PENDING → email

## 7. Cierre

- [x] 7.1 PR con `Closes #<id>` y CI en verde
- [x] 7.2 Merge (PR #180) → deploy SUCCESS; `MAIL_*` (Ethereal) añadidas al `.env` del EC2; prod healthy con el nuevo código (endpoint de alta activo). Email verificado end-to-end por IMAP en el reality-check
