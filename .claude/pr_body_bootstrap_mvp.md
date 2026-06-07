## Contexto

PadelPro tiene ya documentados su modelo de datos, diseño de seguridad, contrato REST y estrategia de testing. La documentación UX está completa. Sin embargo, no existía ningún change OpenSpec ejecutable que un agente pudiese tomar para iniciar la implementación.

Este change resuelve ese bloqueo de dos maneras:
1. **Andamiaje**: consolida el esqueleto OpenSpec (`openspec/project.md`, `openspec/AGENTS.md`) como punto de entrada canónico para todos los agentes futuros.
2. **Primera capability ejecutable**: declara e implementa `auth-local` mínima — registro e inicio de sesión con email y contraseña, emisión de JWT, rate limiting y auditoría de intentos.

Closes #76

---

## Tareas ejecutadas

### DOCS
- [x] T-001 — Verificar `openspec/project.md`
- [x] T-002 — Verificar `openspec/AGENTS.md`
- [x] T-003 — Crear `proposal.md`
- [x] T-004 — Crear `design.md`
- [x] T-005 — Crear `tasks.md`
- [x] T-006 — Crear spec `auth-local/spec.md` con R-1..R-5
- [x] T-007 — Validar enlaces a mockups UX
- [x] T-008 — Issue #76 en Project v2

### BACKEND
- [x] T-009 — Entidad JPA `User`
- [x] T-010 — `UserRepository` Spring Data JPA
- [x] T-011 — `RefreshToken` + `RefreshTokenRepository`
- [x] T-012 — `RegistrationService` (BCrypt 12, política, unicidad email)
- [x] T-013 — `AuthService` (anti-enumeración, audit_log)
- [x] T-014 — `JwtService` HS256 15 min
- [x] T-015 — `AuthController` /register + /login
- [x] T-016 — Spring Security config JWT stateless
- [x] T-017 — Migración Flyway V1 `users`
- [x] T-018 — Migración Flyway V2 `refresh_tokens`
- [x] T-018b — Migración Flyway V3 `audit_log`
- [x] T-019 — Rate limiter Bucket4j (login 5/min, register 3/min)
- [x] T-020 — Auditoría LOGIN_SUCCESS / LOGIN_FAILURE
- [x] T-021 — Tests unitarios `RegistrationService` (8 tests)
- [x] T-022 — Tests unitarios `AuthService` (10 tests)
- [x] T-023 — Tests unitarios `JwtService` (5 tests)
- [x] T-024 — Tests integración Testcontainers
- [x] T-025 — Tests rate limiting

### FRONTEND
- [x] T-026 — SplashPage
- [x] T-027 — LoginPage
- [x] T-028 — RegisterPage
- [x] T-029 — JWT en memoria JS (AuthContext)
- [x] T-030 — PrivateRoute guard
- [x] T-031 — Tests LoginPage (5 tests)
- [x] T-032 — Tests RegisterPage (6 tests)

---

## Scenarios del spec vs test asociado

| Scenario | Test |
|---|---|
| R-1.1 Registro valido | `should_create_user_with_pending_status_when_registration_is_valid` |
| R-1.2 Email duplicado | `should_throw_exception_when_email_already_exists` |
| R-1.3a Contrasena corta | `should_reject_password_shorter_than_8_chars` |
| R-1.3b Sin mayuscula | `should_reject_password_without_uppercase` |
| R-1.3c Sin digito | `should_reject_password_without_number` |
| R-2.1 Login valido | `should_return_token_pair_when_credentials_are_valid` |
| R-2.2 Email inexistente | `should_throw_auth_exception_when_email_not_found` |
| R-2.3 Contrasena incorrecta | `should_throw_auth_exception_when_password_is_wrong` |
| R-2.2+R-2.3 anti-enumeracion | `should_return_same_error_for_unknown_email_and_wrong_password` |
| R-2.4 PENDING/INACTIVE 403 | `should_throw_account_not_active_exception_when_status_is_pending` |
| R-3.1 Token valido | `should_generate_valid_token_with_correct_claims` |
| R-3.2 Token expirado | `should_throw_exception_when_token_is_expired` |
| R-3.3 Token manipulado | `should_throw_exception_when_token_signature_is_tampered` |
| R-4.1 Por debajo del umbral | `login_requests_below_threshold_are_not_rate_limited` |
| R-4.2 Login 429 + Retry-After | `login_rate_limit_should_return_429_after_5_requests_per_minute` |
| R-4.3 Registro 429 | `register_rate_limit_should_return_429_after_3_requests_per_minute` |
| R-5.1 LOGIN_SUCCESS audit_log | `should_log_login_success_to_audit_log` |
| R-5.2 LOGIN_FAILURE con user_id | `should_log_login_failure_with_user_id_when_email_exists` |
| R-5.3 LOGIN_FAILURE null user_id | `should_log_login_failure_with_null_user_id_when_email_not_found` |

---

## Decisiones de design.md materializadas

| Decision | Valor |
|---|---|
| Hashing | BCrypt cost 12 |
| Algoritmo JWT | HS256 (jjwt 0.12.x) |
| Access token | 15 min en memoria JS |
| Refresh token | 7 dias, HttpOnly; SameSite=Strict; Secure; Path=/api/auth/refresh |
| JWT_SECRET | Sin fallback (falla en startup) |
| Politica contrasenas | Min 8, max 128, 1 uppercase, 1 digit. Sin simbolo (RN-AUTH-08) |
| Rate limit login | 5 req/min/IP, Bucket4j 8.7.0 |
| Rate limit registro | 3 req/min/IP |
| Anti-enumeracion | 401 AUTH_INVALID_CREDENTIALS identico para email inexistente y contrasena incorrecta |
| JWT frontend | useState en AuthContext (no localStorage) |

---

## Hallazgos de oleada 4 resueltos

| # | Severidad | Fix |
|---|---|---|
| #88 | Critico | RegisterCommand @JsonProperty snake_case + tests actualizados |
| SEC-1 | Merge blocker | Cookie Path=/api/auth/refresh corregido en AuthController |
| SEC-2 | Merge blocker | JWT_SECRET fallback inseguro eliminado |
| #79 | Medium | UserRepositoryPort creado en domain.port.out |
| #84 | Medium | 3 tests R-5 audit_log anadidos a AuthServiceTest |
| #85/#86/#87 | Low | ESLint, status=PENDING assert, expiresIn dinamico |

---

## Notas pre-produccion

- **X-Forwarded-For**: configurar RemoteIpValve en Tomcat en produccion.
- **Testcontainers**: requieren Docker en el entorno CI.

:robot: Generado con Claude Code (orchestrator agent)
