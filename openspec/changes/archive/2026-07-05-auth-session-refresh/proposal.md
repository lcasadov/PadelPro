## Why

El access token JWT dura 15 minutos (RN-AUTH-09) y **no existe forma de renovarlo**: `AuthService` genera y persiste un refresh token en el login y pone la cookie `refresh_token` (7 días, `Path=/api/auth/refresh`), pero **el endpoint `/api/auth/refresh` nunca se implementó** (el `AuthController` solo expone `/register` y `/login`; `auth-local` lo difirió a "un change posterior"). El frontend tampoco tiene refresh ni interceptor de 401. Consecuencia: pasados 15 minutos, **cualquier escritura de la app falla con 401** y el usuario queda atascado. Se detectó al reservar (feedback 2026-07-05: "No se pudo completar la reserva"), pero afecta a toda la aplicación.

## What Changes

- **[Backend] Nuevo endpoint `POST /api/auth/refresh`.** Lee la cookie `refresh_token`, valida el token (hash en BD, no expirado, no revocado), **rota** el refresh token (revoca el anterior, emite uno nuevo) y devuelve un nuevo access token JWT + nueva cookie `refresh_token`. Responde 401 si la cookie falta, es inválida, está expirada o revocada.
- **[Backend] Revocación en logout** (si se añade `/api/auth/logout` en este change) o al menos revocar el refresh token rotado. Alcance mínimo: rotación en refresh; logout puede quedar fuera si se prefiere acotar.
- **[Frontend] Interceptor global de sesión.** Un interceptor axios que, ante un **401** en cualquier petición autenticada, llama a `/api/auth/refresh`, y si tiene éxito **reintenta la petición original** con el nuevo access token (actualizando `AuthContext`); si el refresh falla, limpia la sesión y **redirige a login**. Evita colisiones con múltiples 401 concurrentes (una sola renovación en vuelo).
- El access token sigue viviendo **solo en memoria** (RN-AUTH-09); la cookie httpOnly sigue siendo la única persistencia del refresh token.

## Capabilities

### New Capabilities
<!-- Ninguna. Se extiende auth-local con el endpoint de refresh que ya estaba previsto. -->

### Modified Capabilities
- `auth-local`: se añade el requirement del endpoint `/api/auth/refresh` (rotación de refresh token + emisión de nuevo access token) y el comportamiento de renovación silenciosa de sesión en el cliente, ambos previstos pero no implementados en el change original.

## Impact

- **Fase del producto**: fase-1 (auth-local es fase-1; es un defecto que rompe el uso real).
- **Backend** (`com.padelpro.auth`): `AuthController` (nuevo `POST /refresh`), `AuthService`/`LoginUseCase` (nuevo caso de uso `refresh(rawRefreshToken)` con rotación), `RefreshTokenRepository` (lookup por hash, marcar revocado), `GlobalExceptionHandler` (401 para refresh inválido). Config de seguridad para permitir `/api/auth/refresh` sin access token.
- **Frontend** (`frontend/src/`): interceptor global en el/los cliente(s) axios (`reservasApi`, `usuariosApi`, `authApi`…) o un axios base compartido; `AuthContext` (setter para el token renovado); manejo de la cola de peticiones durante el refresh.
- **Contrato / `docs/openapi.yaml`**: documentar `POST /api/auth/refresh`.
- **Datos / migraciones**: ninguna (la tabla de refresh tokens ya existe; posible columna `revoked_at` si no está — verificar en la entidad `RefreshToken`).
- **Prioridad**: **alta** — bloquea a `reservas-ui-jugador-fixes` (que solo maneja el 401 localmente) y a cualquier flujo largo de la app.

## Fuera de alcance

- Rediseño del modelo de tokens (rotación de familias, detección de reuse/robo) más allá de revocar el refresh usado. Se puede endurecer en un change de seguridad posterior.
- `/api/auth/password/*` (reset de contraseña) — capability/change aparte.
- Cambiar la duración del access token o la política de almacenamiento en memoria (RN-AUTH-09 se mantiene).
- Logout completo con revocación masiva de sesiones — opcional; si no entra aquí, se declara como seguimiento.
