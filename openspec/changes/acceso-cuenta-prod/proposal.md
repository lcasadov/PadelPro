## Why

La app está desplegada pero **nadie puede iniciar sesión** y el flujo de acceso controlado no se puede demostrar por la interfaz. Es una **demo abierta que debe simular un sistema controlado**: cualquiera puede registrarse, pero el acceso lo gobierna un administrador (registro → `PENDING` → el admin aprueba → `ACTIVE`). Hoy faltan tres piezas para que eso funcione end-to-end:

1. **No existe ningún administrador** (BD prod: 1 usuario, 0 admins, 0 activos) → nadie puede aprobar a nadie.
2. **No hay panel admin en el frontend** → el admin solo podría aprobar por `curl`; la "simulación de control" es invisible. (El backend ya tiene los endpoints: listar, aprobar, activar/desactivar.)
3. **"¿Olvidaste la contraseña?" es un placeholder muerto** (`onClick` vacío) y el login muestra "Credenciales inválidas" también cuando la cuenta está pendiente.

En un sistema controlado, la recuperación de contraseña la gobierna el **admin** (no un reset self-service por email), lo que además evita introducir infraestructura de email en esta fase.

Fase del producto: **fase-1**.

## What Changes

- **Administrador inicial** (desbloquea la cadena): un runner idempotente crea un admin `ACTIVE` desde variables de entorno si no existe ninguno (D1).
- **Panel admin en el frontend** (la pieza que hace visible el control): guard por rol, página de gestión de usuarios (listar con filtro por estado, aprobar pendientes, activar/desactivar) y entrada de navegación visible solo para ADMIN. Reutiliza los endpoints ya existentes en la capability `usuarios`.
- **Reset de contraseña gobernado por el admin** (en vez de email self-service): un nuevo endpoint admin genera una contraseña temporal, la muestra al admin **una sola vez** para que la comunique al usuario, y no expone hashes. El botón "¿Olvidaste la contraseña?" deja de estar muerto: lleva a una pantalla "contacta con el administrador del club".
- **UX de errores de login**: el frontend distingue `ACCOUNT_NOT_ACTIVE` (403, cuenta pendiente) de credenciales inválidas (401), con mensajes claros.
- **Acceso provisional de 2 días para cuentas nuevas** (D8): un usuario recién registrado (`PENDING`) puede usar la app durante 48 h desde el registro; pasado ese plazo sin aprobación del admin, queda bloqueado. Tras registrarse ve una pantalla de confirmación ("pendiente de aprobación; acceso provisional 2 días").
- **Cambio de contraseña forzado tras un reset** (D9): la contraseña temporal marca la cuenta con `must_change_password`; en el siguiente login se exige cambiarla antes de usar la app.

## Capabilities

### New Capabilities
_(ninguna — todo encaja en capabilities existentes)_

### Modified Capabilities
- `auth-local`: bootstrap del primer administrador en un despliegue nuevo; distinción explícita de cuenta-no-activa vs credenciales inválidas en el login; pantalla "olvidé la contraseña → contacta con el administrador".
- `usuarios`: panel admin en el frontend para gestionar usuarios (listar/aprobar/activar/desactivar, ya soportado en backend) y nuevo reset de contraseña gobernado por el admin (genera contraseña temporal de un solo uso a mostrar).

## Impact

- **Backend** (`com.padelpro.usuarios`): nuevo endpoint admin de reset de contraseña que genera una contraseña temporal (BCrypt al persistir) y la devuelve en claro **una vez** en la respuesta; el resto de gestión de usuarios ya existe.
- **Frontend**: nuevo `AdminRoute` (guard `role === ADMIN`); página `/admin/usuarios` (+ servicio `adminUsuariosApi`); entrada de nav condicionada a ADMIN; páginas/rutas `/forgot-password` (pantalla "contacta admin") y el mapeo del error `ACCOUNT_NOT_ACTIVE`; enlazado del botón de `LoginPage`.
- **Config / Secrets**: `ADMIN_EMAIL`/`ADMIN_PASSWORD` en el `.env` del EC2 para el seed (D1).
- **docs/DEPLOYMENT-RUNBOOK.md**: cómo se crea el primer admin en un despliegue nuevo.
- **Sin** infraestructura de email (SMTP), **sin** tabla `otp_codes`, **sin** `spring-boot-starter-mail` en esta fase.

## Fuera de alcance

- **Reset self-service por email/token** (SMTP, plantillas, `otp_codes`): se difiere a cuando exista la capability `notificaciones`. En esta fase el reset lo gobierna el admin.
- OTP por Telegram y bot (`auth-otp-telegram`) y `notificaciones`.
- Auto-activación del registro (se mantiene deliberadamente el flujo `PENDING` → aprobación admin para simular control).
- Verificación de email en el registro.
- Gestión avanzada de roles/permisos y edición de rol desde el panel (USER↔ADMIN) — fuera de la v1.
