## Context

El panel `/admin/usuarios` (React) ya lista, filtra, aprueba, activa/desactiva y resetea contraseñas, pero no da de alta ni edita. El backend ya tiene `POST /api/admin/usuarios` (crea `ACTIVE`, `CreateUserAdminCommand` con `login/firstName/lastName/email/password/phone/role`) y `PATCH /api/admin/usuarios/{id}` (`UpdateUserAdminCommand` con `firstName/lastName/email/phone/role/status`). Existe un `TemporaryPasswordGenerator` y el flag `must_change_password` (del change `acceso-cuenta-prod`). No hay ninguna infraestructura de email. Stack: Spring Boot 3 / JDK 17, hexagonal, BCrypt; frontend con vitest. Preferencia del usuario: **TDD estricto**.

## Goals / Non-Goals

**Goals:**
- Alta y edición de usuarios desde el panel admin.
- Email de bienvenida con credenciales al activar una cuenta.
- Infraestructura de email SMTP configurable (Ethereal para pruebas).

**Non-Goals:**
- Otros emails (reservas, pagos, reset self-service), Telegram, proveedor SMTP productivo, verificación de email en registro.

## Decisions

### D1 — Capability `notificaciones` con un `MailPort` y adaptador SMTP
Se crea un puerto de dominio `NotificationPort`/`MailPort` (envío de email) y un adaptador SMTP (`JavaMailSender`, `spring-boot-starter-mail`). El resto del código depende del puerto, no de JavaMail.
- **Razón:** aísla el proveedor; cambiar Ethereal→SES será solo configuración/adaptador. Respeta la arquitectura hexagonal.

### D2 — Contraseña generada por el sistema en el alta (no la teclea el admin)
El formulario de alta pide nombre, email y rol; la contraseña la genera el backend con el `TemporaryPasswordGenerator` existente, crea el usuario `ACTIVE` con `must_change_password = true`, y la comunica por email. El campo `password` del alta deja de exigirse al admin.
- **Alternativa:** que el admin teclee la contraseña → peor higiene y UX; el admin conocería la contraseña.
- **Razón:** coherente con el reset por admin ya existente; el usuario cambia la temporal en el primer login (D9 de `acceso-cuenta-prod`).

### D3 — Email de bienvenida al ACTIVAR, distinto según el flujo (confirmado con el usuario)
Los dos caminos de activación difieren en si el sistema conoce (o debe emitir) la contraseña:
- **Alta directa (admin crea al momento):** el usuario no tiene contraseña → el sistema genera una temporal (`TemporaryPasswordGenerator`), la persiste BCrypt, marca `must_change_password = true` y la **envía en el email de bienvenida** (es la única forma de que el usuario la conozca).
- **Aprobación de una cuenta PENDING auto-registrada:** el usuario **ya eligió su contraseña** al registrarse. La aprobación **NO la resetea**: envía un email de bienvenida "tu cuenta ha sido aprobada, ya puedes entrar" **sin contraseña** (el usuario usa la suya).
- **Razón:** respeta la contraseña que el usuario eligió, evita reenviar/filtrar contraseñas innecesariamente, y el email de bienvenida tiene sentido en ambos casos. El requisito "indicándole la password" se cumple donde de verdad hace falta: el alta.

### D4 — Envío asíncrono y tolerante a fallos
El email se envía de forma asíncrona (`@Async`); un fallo de SMTP **no** revierte la activación ni propaga error al admin (se registra en logs y opcionalmente en `audit_log`). La contraseña temporal nunca se registra en logs (RN-RGPD-04).
- **Razón:** la disponibilidad del alta/activación no debe depender del proveedor de email; en un entorno de demo con Ethereal el envío puede fallar sin bloquear la gestión.

### D5 — Credenciales SMTP solo por entorno; Ethereal como proveedor inicial
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` se leen del entorno. En el `.env` del EC2 se ponen las de Ethereal (`smtp.ethereal.email:587`); en `.env.example` van placeholders. **Nunca se commitean credenciales reales.** El `docker-compose.yml` las pasa al contenedor backend.
- **Razón:** seguridad (secretos fuera del repo) y portabilidad de proveedor. Ethereal captura los emails sin entregarlos — ideal para validar el flujo en la demo.

### D6 — Frontend: alta y edición como formularios sobre los endpoints existentes
"Dar de alta" abre un formulario (nombre, email, rol) → `POST /api/admin/usuarios`; "Editar" por fila → formulario con los datos actuales → `PATCH /api/admin/usuarios/{id}`. Test de componente primero (TDD).
- **Razón:** el backend ya está; el grueso es UI. Reutiliza `adminUsuariosApi`.

### D7 — Alcance de la edición: datos de contacto, sin editar el rol (confirmado)
El formulario de edición permite modificar **nombre, email y teléfono**. El **rol** (USER↔ADMIN) queda **fuera** del formulario para evitar escalada de privilegios (coherente con la exclusión de edición de rol en `acceso-cuenta-prod` D7). El alta sí fija el rol al crear.
- **Razón:** un admin no debería poder convertir usuarios en admins desde la edición rutinaria; si se quisiera, sería una acción aparte y auditada.

### D8 — "Borrar" = desactivar (soft-delete), no borrado físico (confirmado)
La acción de borrado reutiliza el `DELETE /api/admin/usuarios/{id}` existente, que deja la cuenta `INACTIVE` (soft-delete). No se elimina el registro, para conservar trazabilidad e integridad referencial.
- **Razón:** en un club interesa el histórico; un `INACTIVE` no puede entrar (sin gracia) y no aparece como activo. El borrado físico queda fuera de alcance.

## Risks / Trade-offs

- **Credenciales SMTP filtradas** → abuso del buzón. **Mitigación:** solo en `.env` (no repo), `.gitignore` cubre `.env`; Ethereal es una cuenta de pruebas desechable.
- **Fallo de envío deja al usuario sin conocer su contraseña** → no puede entrar. **Mitigación:** el admin siempre puede "Restablecer contraseña" desde el panel (muestra la temporal en pantalla) como vía de respaldo; el fallo se registra.
- **Email como vector de fuga de credenciales** → en el alta, la contraseña temporal viaja en claro por email. **Mitigación:** es temporal y con `must_change_password`; aceptable para la fase de demo; endurecer (enlace de establecer contraseña) es evolución. En la aprobación no viaja ninguna contraseña (D3).
- **`@Async` sin configurar** → se ejecuta síncrono. **Mitigación:** habilitar `@EnableAsync` y un executor; test que verifica que un fallo de mail no rompe la activación.

## Migration Plan

1. Añadir `spring-boot-starter-mail`; configurar SMTP por entorno; `@EnableAsync`.
2. Poner las credenciales Ethereal en el `.env` del EC2 y el passthrough en `docker-compose.yml`.
3. Deploy: el alta/edición/activación con email quedan operativos; los emails se ven en el buzón de Ethereal.
4. **Rollback:** additivo; el email es tolerante a fallos, así que desactivarlo (sin `MAIL_*`) no rompe la gestión de usuarios.

## Open Questions

- ¿El email de bienvenida incluye también un enlace directo a la app (`http://<host>:5173`)?
- ¿Se registra el envío/fallo del email en `audit_log` (acción `WELCOME_EMAIL_SENT`)?
