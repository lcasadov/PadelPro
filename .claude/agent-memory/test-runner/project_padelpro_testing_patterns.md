---
name: project-padelpro-testing-patterns
description: PadelPro-specific testing patterns: STRICT_STUBS workaround for TDD-red phase, ArchUnit violation found in skeleton, Testcontainers setup
metadata:
  type: project
---

## PadelPro — key testing patterns discovered (Sprint 1 TDD cycle)

**STRICT_STUBS in TDD-red phase**: Mockito STRICT_STUBS fires `UnnecessaryStubbingException` when stubs are set up but the SUT throws `UnsupportedOperationException` before reaching the stubbed call. Fix: use `org.mockito.Mockito.lenient().when(...)` on all stubs that will only be consumed once Wave 3 implements the method. Remove `lenient()` in Wave 3 once stubs are consumed.

**Why:** STRICT_STUBS is mandatory per TESTING-STRATEGY.md §3.1 — we can't downgrade to LENIENT globally. Per-stub lenient is the precise fix.

**ArchUnit violation detected in Wave 2 skeleton**: `AuthService` and `RegistrationService` inject `UserRepository` (infrastructure.persistence) directly instead of going through a domain port interface. ArchUnit rule `application_should_not_depend_on_infrastructure` catches this. The rule is kept RED intentionally — Wave 3 must introduce `UserRepositoryPort` in `domain.port.out`.

**How to apply:** When writing ArchUnit rules for application→infrastructure boundaries in PadelPro, expect this violation until Wave 3 fixes the DIP. Document it in the rule's `@ArchTest` `.because()` clause.

**Testcontainers static container warning**: The IDE reports "Resource leak: unassigned Closeable" on `static final PostgreSQLContainer<?>`. This is a false positive — Testcontainers manages the lifecycle via JUnit 5 `@Testcontainers` + `@Container`. No action needed.

**@BeforeEach + @Sql combination**: Using `@Sql("/sql/cleanup.sql")` on `@BeforeEach` works correctly with Testcontainers PostgreSQL 15. The profile `it` must be active (`@ActiveProfiles("it")`).

**Java version**: Build host has JDK 17 (Temurin at `/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot`). pom.xml targets 17 for local compilation; CI/Docker runs 21. Set `JAVA_HOME` before running mvn.

**Canonical mvn command for unit tests (no Docker needed):**
`mvn test -Dtest="RegistrationServiceTest,AuthServiceTest,JwtServiceTest,HexagonalArchitectureTest" -DfailIfNoTests=false`
