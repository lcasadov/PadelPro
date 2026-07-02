## Context

PadelPro está en producción pero el acceso es inutilizable y el relato de "sistema controlado" no se puede demostrar por la UI. La activación de cuentas ya existe en `usuarios` (backend): `GET /api/admin/usuarios` (listar con filtro de estado), `PATCH /api/admin/usuarios/{id}/aprobar` (PENDING→ACTIVE), `PATCH /api/admin/usuarios/{id}` (rol/estado), `DELETE` (desactivar). Lo que falta es: (a) un primer admin que arranque la cadena, (b) **interfaz** para que el admin gobierne el acceso, y (c) una vía de recuperación de contraseña coherente con el control.

Frontend: React + Vite. `role` ya viaja en la respuesta de login y se muestra en el perfil; `PrivateRoute` solo comprueba autenticación (no rol). No hay servicio ni páginas admin. `UpdateUserAdminCommand` no admite contraseña. No hay SMTP ni `otp_codes`.

## Goals / Non-Goals

**Goals:**
- Que exista siempre un admin `ACTIVE` tras un despliegue nuevo.
- Que el admin gobierne el acceso **desde la UI**: ver pendientes, aprobar, activar/desactivar, y resetear contraseñas.
- Que el usuario entienda por qué no puede entrar (pendiente vs credenciales) y dónde acudir si olvida la contraseña.

**Non-Goals:**
- Reset self-service por email (SMTP/`otp_codes`) — diferido a `notificaciones`.
- Auto-activación del registro — se mantiene el flujo controlado.
- OTP Telegram / bot / notificaciones.

## Decisions

### D1 — Bootstrap del primer admin vía runner idempotente con credenciales de entorno
Un `ApplicationRunner` crea un admin `ACTIVE` solo si no existe ningún ADMIN, leyendo `ADMIN_EMAIL`/`ADMIN_PASSWORD` del entorno y cifrando con BCrypt. Idempotente; si faltan las variables no crea nada y no rompe el arranque.
- **Alternativa:** seed por migración Flyway → no puede aplicar BCrypt ni leer entorno sin hash hardcodeado (inseguro).
- **Razón (RN-AUTH-07):** hash en runtime desde un secreto del entorno, idempotente y sin credenciales en el repo.

### D2 — Se mantiene `PENDING` + aprobación admin (no auto-activación)
El registro deja `PENDING`; el admin aprueba. Es el comportamiento que la demo debe **simular** (control de acceso real).
- **Razón:** auto-activar rompería la narrativa de sistema controlado; además el backend de aprobación ya existe.

### D3 — Recuperación de contraseña gobernada por el admin (no email self-service)
"¿Olvidaste la contraseña?" lleva a una pantalla "contacta con el administrador del club". El admin, desde el panel, dispara un reset que **genera una contraseña temporal**; el sistema la devuelve en claro **una sola vez** en la respuesta para que el admin la comunique, y la persiste cifrada (BCrypt).
- **Alternativa A:** reset self-service por email + token (`otp_codes`, SMTP) → introduce infraestructura de email y no encaja con el modelo controlado de esta fase.
- **Alternativa B:** el admin teclea la contraseña → el admin conocería/elegiría contraseñas (peor higiene).
- **Razón:** la generación por el sistema evita que el admin elija contraseñas, no expone hashes, reutiliza el panel admin que ya se construye, y refuerza el relato de control sin SMTP.

### D4 — La contraseña temporal se muestra una sola vez y nunca se loguea
La respuesta del endpoint de reset incluye la contraseña temporal en claro exactamente una vez; no se persiste en claro ni aparece en logs (RN-RGPD-04).
- **Razón:** minimizar la exposición del secreto temporal.

### D5 — Distinción de error en login en el frontend (D6 de la versión previa)
El backend ya devuelve `403 ACCOUNT_NOT_ACTIVE` vs `401`; el frontend mapea cada uno a un mensaje distinto.
- **Razón (UX):** el usuario sabe si debe esperar aprobación o revisar credenciales.

### D6 — Guard de rol en el frontend y nav condicionada
Nuevo `AdminRoute` (envuelve rutas que exigen `role === ADMIN`); la entrada de navegación al panel solo se muestra a ADMIN. La autorización real la sigue imponiendo el backend (`/api/admin/**` requiere ROLE_ADMIN); el guard de frontend es UX/defensa en profundidad.
- **Razón:** evitar que un USER vea u opere el panel; el backend es la barrera autoritativa.

### D7 — El panel admin reutiliza los endpoints existentes (alcance v1 completo)
La página `/admin/usuarios` consume `GET /api/admin/usuarios` (listar/filtrar), `PATCH .../aprobar`, `PATCH .../{id}` (estado), `DELETE .../{id}` (desactivar) y el nuevo reset. **Alcance v1: aprobar pendientes + activar/desactivar + resetear contraseña.** La edición de rol (USER↔ADMIN) queda fuera de v1.
- **Razón:** el backend de gestión ya está; el trabajo es de frontend. Excluir la edición de rol reduce el riesgo de escalada de privilegios y respeta la protección de identidad del ADMIN (usuarios Requirement 3).

### D8 — Periodo de gracia de 2 días para cuentas PENDING
Un usuario recién registrado (`PENDING`) SHALL poder iniciar sesión y usar la app durante **48 horas desde su registro**. Pasado ese plazo, si sigue `PENDING` (no aprobado), el login SHALL devolver `403 ACCOUNT_NOT_ACTIVE`. La aprobación del admin lo deja `ACTIVE` de forma permanente.
- **Alternativa:** bloquear `PENDING` desde el primer momento → la demo no sería "abierta" (nadie probaría sin que un admin apruebe primero).
- **Razón:** concilia "demo abierta" (acceso inmediato provisional) con "simula control" (el admin debe aprobar para acceso continuado). La ventana se calcula sobre `users.registered_at` (timestamp de registro; no existe columna `created_at`). La transición a `INACTIVE` (desactivado por admin) no tiene gracia: bloquea siempre.

### D9 — Cambio de contraseña forzado tras un reset
Una contraseña temporal (generada por el admin en un reset) SHALL marcar la cuenta con `must_change_password = true`. En el siguiente login, el sistema SHALL exigir el cambio de contraseña antes de permitir el uso normal de la app; tras cambiarla, el flag se limpia.
- **Alternativa:** solo recomendar el cambio → el usuario podría seguir con una contraseña que el admin conoció.
- **Razón:** la contraseña temporal la conoce (transitoriamente) el admin al comunicarla; forzar el cambio cierra esa ventana. Aplica también, opcionalmente, al admin sembrado (D1) si se quiere endurecer.

## Decisiones cerradas (antes Open Questions)

- **Forzar cambio de la contraseña temporal:** SÍ (D9).
- **Alcance del panel v1:** aprobar + activar/desactivar + reset; sin edición de rol (D7).
- **Pantalla post-registro:** SÍ — tras registrarse, el usuario ve "cuenta creada, pendiente de aprobación; tienes acceso provisional durante 2 días" (alineado con D8).

## Open Questions

- ¿El periodo de gracia se comunica con una cuenta atrás visible (días restantes) en la UI, o solo como aviso?
- ¿El cambio forzado (D9) aplica también al admin sembrado en su primer login, o solo a usuarios reseteados?

## Risks / Trade-offs

- **`ADMIN_PASSWORD` débil/filtrada (D1)** → admin comprometido. **Mitigación:** longitud mínima; `.env` fuera del repo; rotación documentada.
- **Contraseña temporal interceptada al comunicarla (D3/D4)** → acceso indebido. **Mitigación:** mostrar una sola vez; TTL conceptual corto comunicado al usuario; (futuro) forzar cambio en el primer login.
- **Guard de frontend confundido con seguridad (D6)** → si alguien cree que el guard protege, podría relajar el backend. **Mitigación:** documentar que el backend (`/api/admin/**` = ROLE_ADMIN) es la barrera real; el guard es solo UX.
- **El admin se desactiva/elimina a sí mismo desde el panel** → lockout. **Mitigación:** el backend ya protege la identidad del ADMIN (usuarios Requirement 3, RN-AUTH-05); el panel debe respetar esos errores.
- **Sin forzar cambio de la contraseña temporal** → el usuario podría no cambiarla. **Mitigación:** comunicarlo en UX; dejar el forzado como mejora futura (Open Questions).

## Migration Plan

1. Desplegar el backend con `ADMIN_EMAIL`/`ADMIN_PASSWORD` en el `.env` del EC2 → el runner crea el admin en el primer arranque.
2. El frontend (panel + reset + pantallas) se despliega con el mismo pipeline.
3. **Rollback:** additivo; el endpoint de reset y el panel se pueden retirar sin afectar datos; el admin sembrado se desactiva con el endpoint existente.

## Open Questions

- ¿Forzar el cambio de la contraseña temporal en el primer login? (recomendado; requiere un flag `must_change_password` y un flujo de cambio — evaluar coste).
- ¿Alcance exacto del panel v1: solo aprobar pendientes, o también activar/desactivar, cambiar rol y resetear desde el inicio?
- ¿Conviene una pantalla "mi cuenta está pendiente" tras el registro (además del mensaje en el login)?
