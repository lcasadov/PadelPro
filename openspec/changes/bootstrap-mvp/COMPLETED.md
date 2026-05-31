# COMPLETED: bootstrap-mvp

**Fecha de cierre:** 2026-05-24
**Commit SHA (último):** d723ab6
**PR:** feat/openspec-bootstrap-mvp → develop

---

## Resumen

Change `bootstrap-mvp` implementado y verificado. Cubre el andamiaje OpenSpec inicial y la capability `auth-local` mínima (registro + login + JWT + rate limiting + auditoría).

---

## Specs fusionadas

| Capability | Destino |
|---|---|
| `auth-local` | `openspec/specs/auth-local/spec.md` |

---

## Tests añadidos

### Backend (JUnit 5 + AssertJ + Mockito + Testcontainers)

| Archivo | Tests | Scenarios cubiertos |
|---|---|---|
| `RegistrationServiceTest` | 8 | R-1.1 a R-1.4 |
| `AuthServiceTest` | 10 | R-2.1 a R-2.4, R-5.1 a R-5.3 |
| `JwtServiceTest` | 5 | R-3.1 a R-3.3 |
| `HexagonalArchitectureTest` | 5 | Boundaries hexagonales |
| `AuthControllerIntegrationTest` | ~10 | R-1.x, R-2.x (Testcontainers) |
| `RateLimitIntegrationTest` | 4 | R-4.1 a R-4.3 (Testcontainers) |

### Frontend (Vitest + React Testing Library + MSW)

| Archivo | Tests | Scenarios cubiertos |
|---|---|---|
| `LoginPage.integration.test.tsx` | 5 | Login OK, 401, 403, 429, redirección |
| `RegisterPage.integration.test.tsx` | 6 | Registro OK, 400, 409, 429 |

---

## Archivos creados

### Backend
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V1__create_users_table.sql`
- `backend/src/main/resources/db/migration/V2__create_refresh_tokens_table.sql`
- `backend/src/main/resources/db/migration/V3__create_audit_log_table.sql`
- `backend/src/main/java/com/padelpro/auth/domain/model/User.java`
- `backend/src/main/java/com/padelpro/auth/domain/model/UserRole.java`
- `backend/src/main/java/com/padelpro/auth/domain/model/UserStatus.java`
- `backend/src/main/java/com/padelpro/auth/domain/model/RefreshToken.java`
- `backend/src/main/java/com/padelpro/auth/domain/model/AuditLog.java`
- `backend/src/main/java/com/padelpro/auth/domain/port/out/UserRepositoryPort.java`
- `backend/src/main/java/com/padelpro/auth/application/dto/RegisterCommand.java`
- `backend/src/main/java/com/padelpro/auth/application/dto/LoginCommand.java`
- `backend/src/main/java/com/padelpro/auth/application/dto/TokenPair.java`
- `backend/src/main/java/com/padelpro/auth/application/dto/UserDto.java`
- `backend/src/main/java/com/padelpro/auth/application/service/RegistrationService.java`
- `backend/src/main/java/com/padelpro/auth/application/service/AuthService.java`
- `backend/src/main/java/com/padelpro/auth/application/service/JwtService.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/persistence/UserRepository.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/persistence/RefreshTokenRepository.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/persistence/AuditLogRepository.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/web/AuthController.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/web/GlobalExceptionHandler.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/web/filter/RateLimitFilter.java`
- `backend/src/main/java/com/padelpro/auth/infrastructure/config/SecurityConfig.java`

### Frontend
- `frontend/` (proyecto completo Vite + React 18 + TypeScript)
- `frontend/src/context/AuthContext.tsx`
- `frontend/src/guards/PrivateRoute.tsx`
- `frontend/src/services/authApi.ts`
- `frontend/src/pages/SplashPage.tsx`
- `frontend/src/pages/LoginPage.tsx`
- `frontend/src/pages/RegisterPage.tsx`

---

## Decisiones materializadas

| Decisión | Valor implementado |
|---|---|
| Hashing de contraseñas | BCrypt cost 12 (`BCryptPasswordEncoder(12)`) |
| Algoritmo JWT | HS256 (`Jwts.SIG.HS256`) |
| Duración access token | 15 minutos (configurable vía `app.jwt.access-token-expiry-minutes`) |
| Duración refresh token | 7 días (`Max-Age=604800`) en cookie `HttpOnly; SameSite=Strict; Secure` |
| Cookie path | `/api/auth/refresh` (security-design.md §2.5) |
| JWT_SECRET | Sin fallback — app no arranca sin env var |
| Política de contraseñas | Min 8, max 128, ≥1 uppercase, ≥1 digit. Sin requisito de símbolo (RN-AUTH-08) |
| Rate limiting login | 5 req/min/IP → 429 + Retry-After (Bucket4j 8.7.0) |
| Rate limiting registro | 3 req/min/IP → 429 + Retry-After |
| Anti-enumeración | 401 AUTH_INVALID_CREDENTIALS idéntico para email inexistente y contraseña incorrecta |
| Cuenta PENDING/INACTIVE | 403 ACCOUNT_NOT_ACTIVE (no 401) |
| Almacenamiento JWT frontend | `useState` en AuthContext (no localStorage/sessionStorage) |
| Hash refresh token en BD | SHA-256 del UUID (no se almacena el token raw) |

---

## Decisiones aplazadas

| Decisión | Change futuro |
|---|---|
| Refresco de token y logout | `auth-session-management` |
| Recuperación de contraseña | `auth-password-reset` |
| Vinculación Telegram | `auth-otp-telegram` |
| Bloqueo tras 10 fallos (RN-AUTH-06) | `auth-lockout` |
| Verificación de email al registrarse | `auth-email-verification` |

---

## Hallazgos resueltos durante la oleada 4

| Bug | Descripción | Resolución |
|---|---|---|
| #79 | ArchUnit: AuthService inyectaba UserRepository (infra) | Creado UserRepositoryPort en domain.port.out |
| #84 | R-5 sin tests de auditoría | 3 tests añadidos a AuthServiceTest |
| #85 | ESLint: variable capturedSetAccessToken no usada | Variable eliminada |
| #86 | R-1.1 no verificaba status=PENDING | ArgumentCaptor añadido |
| #87 | TokenPair.expiresIn hardcodeado a 900 | Derivado de JwtService.getExpirySeconds() |
| #88 | RegisterCommand camelCase vs frontend snake_case | @JsonProperty añadido; tests actualizados |
| SEC-1 | Cookie Path=/ en lugar de /api/auth/refresh | Set-Cookie corregido en AuthController |
| SEC-2 | JWT_SECRET con fallback inseguro en application.yml | Fallback eliminado |
