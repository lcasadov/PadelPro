## Why

El panel de administración de usuarios (`/admin/usuarios`) permite aprobar, activar/desactivar y restablecer contraseñas, pero **no permite dar de alta usuarios nuevos ni editar sus datos** desde la interfaz — aunque el backend ya expone `POST /api/admin/usuarios` (alta) y `PATCH /api/admin/usuarios/{id}` (edición). Además, cuando el administrador **activa** una cuenta, el usuario no recibe ninguna notificación: se quiere que reciba un **email de bienvenida con su contraseña** para poder entrar. Esto introduce la primera pieza de la capability de **notificaciones** (envío de email vía SMTP), usando de momento una cuenta de prueba (Ethereal).

Fase del producto: **fase-1**.

## What Changes

- **Alta de usuarios desde el panel** (frontend): formulario "Dar de alta" (nombre, email, rol) que crea un usuario `ACTIVE`. La contraseña la **genera el sistema** y se comunica por email (no la teclea el admin), marcando `must_change_password`.
- **Edición de usuarios desde el panel** (frontend): acción "Editar" por fila que permite modificar los **datos de contacto** del usuario (nombre, email, teléfono) vía `PATCH /api/admin/usuarios/{id}`. El **rol no** se edita desde este formulario (evita escalada de privilegios).
- **Borrar usuario** = desactivar (soft-delete) reutilizando el `DELETE` existente (la cuenta queda `INACTIVE`, no se elimina el registro).
- **Email de bienvenida al activar** (nuevo, backend), según el flujo:
  - **Alta directa**: email con la contraseña temporal generada por el sistema.
  - **Aprobación de una cuenta PENDING**: email "tu cuenta ha sido aprobada, ya puedes entrar" **sin** contraseña (el usuario usa la que eligió al registrarse; no se resetea).
  El envío es asíncrono y **no bloquea** la activación; su fallo se registra pero no revierte la operación.
- **Infraestructura de email** (nueva capability `notificaciones`): integración SMTP (`spring-boot-starter-mail`) con credenciales configurables por entorno; proveedor inicial **Ethereal** (SMTP de pruebas que captura los emails).

## Capabilities

### New Capabilities
- `notificaciones`: envío de emails transaccionales vía SMTP (empezando por el email de bienvenida/activación de cuenta), con proveedor y credenciales configurables por entorno.

### Modified Capabilities
- `usuarios`: alta y edición de usuarios desde el panel de administración (interfaz sobre los endpoints existentes), y disparo del email de bienvenida al activar una cuenta.

## Impact

- **Frontend** (`AdminUsuariosPage` + `adminUsuariosApi`): formulario de alta, formulario/acción de edición, y su integración con los endpoints existentes.
- **Backend** (`com.padelpro.usuarios` + nuevo `notificaciones`):
  - Generación de contraseña temporal en el alta (reutilizando el generador existente del reset) y en la activación.
  - Servicio de email (`JavaMailSender`) + plantilla de bienvenida; disparo asíncrono al activar/crear ACTIVE.
  - `spring-boot-starter-mail` en el `pom.xml`.
- **Config / Secrets**: credenciales SMTP en el `.env` del EC2 (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) y placeholders en `.env.example`. **Nunca se commitean credenciales reales.** Passthrough en `docker-compose.yml` (bloque `environment` del backend).
- **docs**: `openapi.yaml` (alta/edición ya existen; documentar el efecto del email si aplica) y runbook (config SMTP).

## Fuera de alcance

- Otros emails transaccionales (reset por email self-service, notificaciones de reservas/pagos, Telegram) — se irán añadiendo sobre la capability `notificaciones`.
- Proveedor SMTP de producción real (SES, Mailgun): en esta fase se usa Ethereal para pruebas; cambiar de proveedor será solo configuración.
- Verificación de email / doble opt-in en el registro.
- Edición del propio perfil por el usuario (ya existe `PATCH /api/usuarios/me`).
