## Context

`AuthService.login` ya persiste un `RefreshToken` (hash SHA-256, expiración 7 días) y el `AuthController` pone la cookie `refresh_token; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh`. Falta la otra mitad: **consumir** ese refresh token. No hay endpoint `/api/auth/refresh`, ni caso de uso de refresh, ni interceptor en el frontend. El access token dura 900s (RN-AUTH-09). Confirmado en vivo (E2E 2026-07-05): tras caducar, `POST /api/reservas` → 401 `AUTH_REQUIRED`.

Este change cierra el círculo previsto por `auth-local` (que difirió estos endpoints). El client-side no puede leer la cookie httpOnly (correcto por diseño): el navegador la envía sola a `Path=/api/auth/refresh`.

## Goals / Non-Goals

**Goals:**
- Renovar el access token sin re-login mientras el refresh token (7 días) siga vivo.
- Que el usuario no vea nunca un 401 opaco en un flujo normal dentro de la ventana de 7 días.
- Rotación del refresh token en cada uso (el usado se revoca) — mitiga replay básico.
- Renovación **silenciosa** en el cliente: 401 → refresh → reintento transparente.

**Non-Goals:**
- Detección avanzada de reuse/robo de tokens (familias, breach response).
- Cambiar RN-AUTH-09 (access token en memoria, 15 min).
- Reset de contraseña ni logout masivo (fuera de alcance / seguimiento).

## Decisions

### D1 — `POST /api/auth/refresh`: rotación con revocación del token usado
El endpoint lee la cookie, hashea (SHA-256) y busca el `RefreshToken` por hash; valida no expirado y no revocado; **revoca el usado** y **emite uno nuevo** (misma expiración relativa o la restante — decisión: nueva ventana de 7 días para no expulsar a usuarios activos), devolviendo nuevo access token + nueva cookie. Motiva RN-AUTH-09. *Alternativa descartada:* no rotar (reutilizar el mismo refresh) — más simple pero sin mitigación de replay; la rotación es barata con la tabla existente.

### D2 — Refresh sin access token, protegido solo por la cookie
`/api/auth/refresh` debe ser accesible sin `Authorization` (el access token está caducado, por eso se refresca). La seguridad recae en la cookie httpOnly `SameSite=Strict` + el hash en BD. Se añade a la allowlist de la config de seguridad, con rate limiting como el resto de endpoints públicos de auth (RN-AUTH: rate limiting). *Alternativa descartada:* exigir el access token caducado en el body — no aporta seguridad y complica el cliente.

### D3 — Interceptor de cliente con una sola renovación en vuelo
Un interceptor de respuesta axios detecta 401, y si no es ya una petición de refresh: encola la petición original, dispara **un único** `/api/auth/refresh` (aunque varias peticiones fallen a la vez), y al resolverse reintenta todas con el nuevo token. Si el refresh falla (401): limpia `AuthContext` y redirige a login. Se marca la petición reintentada para no entrar en bucle. Motiva la renovación silenciosa. *Alternativa descartada:* refrescar proactivamente por temporizador antes de exp — más código y relojes que desincronizan; reaccionar al 401 es robusto y simple.

### D4 — Un axios base compartido
Hoy cada service (`reservasApi`, `usuariosApi`, `authApi`) crea su propio `axios.create`. Para no duplicar el interceptor, se centraliza en una instancia base compartida (o un helper que registre el interceptor en cada una). Decisión: instancia base compartida reutilizada por los services. *Alternativa considerada:* registrar el interceptor N veces — funciona pero duplica la lógica de cola; se prefiere centralizar.

## Risks / Trade-offs

- [Bucle de refresh si `/api/auth/refresh` devuelve 401 y el interceptor lo reintenta] → Mitigación: excluir explícitamente la URL de refresh del interceptor y marcar la petición ya-reintentada.
- [Condición de carrera con múltiples 401 concurrentes lanzando múltiples refresh y rotando el token varias veces] → Mitigación: patrón "single-flight" (una promesa de refresh compartida); las demás peticiones esperan a esa promesa.
- [La cookie `Secure` no viaja sobre http en dev/EC2 por IP] → Mitigación: verificar el esquema en el entorno objetivo; documentar que producción debe servirse por HTTPS (o ajustar `Secure` según entorno, sin debilitar producción).
- [Rotar a 7 días nuevos en cada refresh permite sesión perpetua para un usuario activo] → Mitigación aceptada (UX): es el comportamiento estándar de refresh rotativo; el endurecimiento (límite absoluto de sesión) se puede añadir después.

## Migration Plan

- Sin migración si la entidad `RefreshToken` ya tiene el campo de revocación; si no, añadir `revoked_at` (nullable) vía Flyway — verificar `RefreshToken.java` y el esquema antes de decidir.
- Despliegue: backend (endpoint) y frontend (interceptor) juntos; ambos aditivos y retrocompatibles. Rollback = revertir ambos; sin efecto sobre login/register existentes.

## Open Questions

- ¿Entra `/api/auth/logout` (revocar refresh al cerrar sesión) en este change o se deja como seguimiento? Recomendación: incluir un logout mínimo que revoque el refresh actual, por higiene.
- ¿Nueva ventana de 7 días en cada refresh (sesión deslizante) o conservar la expiración original? Recomendación: deslizante (D1) por UX.
