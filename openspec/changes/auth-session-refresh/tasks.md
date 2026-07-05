# Renovación de sesión (endpoint refresh + interceptor cliente) — fase-1

> Orden TDD (Red → Green → Refactor). Prioridad alta: desbloquea el manejo real del 401 en `reservas-ui-jugador-fixes` y en toda la app. Verificar antes de codear si `RefreshToken.java` ya tiene campo de revocación (si no, añadir `revoked_at` vía Flyway).

## 1. Backend — endpoint `POST /api/auth/refresh`

- [x] 1.1 Verificar la entidad `RefreshToken` y el esquema: ¿existe campo de revocación? → **Sí, existe `revoked` (boolean, NOT NULL, default false) en `RefreshToken.java`. No hace falta migración.**
- [x] 1.2 **[Red]** Test del caso de uso `refresh`: cookie válida → nuevo access token + rotación (viejo revocado); ausente/expirado/revocado → 401; reuso del revocado → 401
- [x] 1.3 **[Green]** Caso de uso `refresh(rawRefreshToken)` en la capa de aplicación: lookup por hash SHA-256, validar expiración/revocación, revocar el usado, emitir nuevo refresh (ventana 7 días) + nuevo access token
- [x] 1.3.1 **[Fix seguridad MEDIO-1 + BAJO-1]** Rotación atómica: la revocación del refresh usado ya no es check-then-set sobre la entidad cargada (carrera → doble gasto). Se añade `int revokeByTokenHashIfActive(String)` en el port + `@Modifying @Query UPDATE ... WHERE token_hash=:h AND revoked=false` en el repo JPA; `AuthService.refresh` es `@Transactional` y solo emite tokens nuevos si la revocación condicional afecta 1 fila (0 → 401). Cubierto por unit test `should_reject_reuse_when_revoke_returns_zero` e IT `RefreshTokenRevokeAtomicIT` (1 la primera vez, 0 en el reuso) contra Postgres real.
- [x] 1.4 **[Red]** Test de integración web: `POST /api/auth/refresh` con/ sin cookie, esquema de respuesta `{access_token, token_type, expires_in}` + `Set-Cookie`
- [x] 1.5 **[Green]** `AuthController.refresh` (lee cookie `refresh_token`, setea nueva cookie); allowlist en la config de seguridad para `/api/auth/refresh` sin access token; rate limiting como el resto de auth público
- [x] 1.6 **[Green]** Mapear refresh inválido a 401 en `GlobalExceptionHandler`; documentar en `docs/openapi.yaml`

## 2. Frontend — interceptor de renovación silenciosa

- [x] 2.1 **[Red]** Test (MSW): 401 en petición autenticada → llama a `/api/auth/refresh` → reintenta original con nuevo token → éxito; refresh 401 → limpia sesión y redirige a login; sin bucle sobre la URL de refresh
- [x] 2.2 **[Red]** Test: múltiples 401 concurrentes → un solo `/api/auth/refresh` (single-flight) → todas reintentadas
- [x] 2.3 **[Green]** Instancia axios base compartida con interceptor de respuesta 401 (single-flight: promesa de refresh compartida, cola de peticiones, flag anti-bucle)
- [x] 2.4 **[Green]** `AuthContext`: setter para el access token renovado; en fallo de refresh limpiar sesión
- [x] 2.5 **[Green]** Migrar los services (`reservasApi`, `usuariosApi`, `authApi`…) a la instancia base compartida

## 3. Integración con reservas-ui-jugador-fixes

- [ ] 3.1 Verificar que, con el interceptor activo, crear reserva tras >15 min ya no muestra "sesión caducada" sino que se renueva transparentemente (el manejo local del 401 de reservas queda como fallback) — **PENDIENTE de live E2E: requiere desplegar el build de la rama (el stack de dev corre un build previo sin el endpoint). Cubierto a nivel de contrato por el IT del refresh + tests MSW del interceptor.**

## 4. QA

- [x] 4.1 **[Refactor]** Limpieza manteniendo verde — código en verde, sin deuda pendiente
- [x] 4.2 `security-auditor`: sin CRÍTICOS ni ALTOS; MEDIO-1 (rotación no atómica) corregido con revocación compare-and-set + @Transactional (commit 8bf023d); hallazgos BAJO (revalidar estado de cuenta en refresh, cota absoluta de sesión, auditoría TOKEN_REFRESHED) documentados como endurecimiento futuro
- [x] 4.3 `verification-specialist`: 5/5 unit backend + 130/130 frontend + tsc/eslint limpios; IT del refresh verde contra Postgres real (:5433). Veredicto: PASS (los ITs bloqueados por Testcontainers se corrieron vía perfil de PG real)
- [ ] 4.4 `reality-checker`: sesión larga end-to-end (login → caducidad del access → acción → renovación transparente) — **PENDIENTE de live E2E: requiere desplegar el build de la rama**
