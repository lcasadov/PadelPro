# PadelPro — Estrategia de Testing

> **Versión:** 1.0 · **Fecha:** 2026-05-22  
> **Generado por:** `test-strategist`  
> **Fuente de verdad para:** `tester-tdd`, `test-runner`, `api-tester`, `verification-specialist`, `reality-checker`, `database-optimizer`  
>
> Este documento es **ley**. Toda decisión de testing, todo umbral, todo comando canónico se lee aquí.  
> Los agentes no tienen defaults propios mientras este fichero exista.

---

## 1. Executive Summary

### Objetivo de la estrategia

PadelPro es un sistema de reservas de pádel con integración nativa en Telegram, pasarela de pago Redsys (HMAC SHA-256) y requisitos estrictos de RGPD. La estrategia de testing tiene como objetivo garantizar que:

- El dominio de negocio (reservas, pagos, OTP, auditoría) funcione correctamente y sea testeable de forma totalmente aislada de la infraestructura.
- Las integraciones externas (Redsys, Telegram Bot API, SMTP) estén validadas mediante doubles controlados (WireMock) y no introduzcan fragilidad en la suite.
- La restricción de solapamiento de reservas (`EXCLUDE USING gist` + `SELECT FOR UPDATE`) esté cubierta con tests de concurrencia reales sobre PostgreSQL 15 — nunca con H2.
- Ninguna credencial, token, OTP en claro ni dato personal sensible aparezca en logs ni en respuestas de error.
- El pipeline de CI falle automáticamente si cualquier umbral de cobertura o calidad no se alcanza.

### Stack de testing consolidado

| Capa | Framework | Versión | Notas |
|---|---|---|---|
| Backend — unit | JUnit 5 + AssertJ + Mockito | Bundled con Spring Boot 3.2 | Strict stubs activados por defecto |
| Backend — integration | Spring Boot Test + MockMvc + Testcontainers + WireMock | Testcontainers 1.19.x | PostgreSQL 15 real; WireMock para Redsys y Telegram |
| Backend — architecture | ArchUnit | 1.3.x | Fronteras hexagonales por módulo |
| Backend — mutation | Pitest (PIT) | 1.15.x | Módulos: `pagos`, `otp`, `reservas` |
| Backend — coverage | JaCoCo | Bundled con Spring Boot | HTML + XML; quality gate en Maven |
| Frontend — unit/components | Vitest + React Testing Library | Vitest 1.x + RTL 15.x | Nativo ESM, alineado con Vite |
| Frontend — API mock | MSW (Mock Service Worker) | 2.x | Handlers en `frontend/tests/mocks/` |
| Frontend — E2E | Playwright | 1.44.x | Chromium obligatorio; Firefox opcional |
| Frontend — coverage | c8 (via Vitest) | Incluido en Vitest | Umbral en `vitest.config.ts` |
| CI | GitHub Actions | — | Jobs: build, unit-tests, integration-tests, coverage, quality-gate, lint |

### Umbrales de cobertura globales

| Métrica | Mínimo | Objetivo |
|---|---|---|
| Líneas (backend) | 80 % | 85 % |
| Branches (backend) | 75 % | 80 % |
| Líneas (frontend) | 75 % | 80 % |
| Flujos críticos | **100 %** | 100 % |
| Reglas de negocio (RN-xx) | **100 %** | 100 % |
| Mutation score (módulos críticos) | 70 % | 75 % |
| Duplicación de código | < 3 % | < 2 % |

### Quality gate — build fails if

- Cualquier test unitario o de integración falla.
- La cobertura de líneas baja del 80 % (backend) o del 75 % (frontend).
- La cobertura de branches baja del 75 % (backend).
- Cualquier flujo crítico o regla de negocio declarada en esta estrategia tiene cobertura < 100 %.
- El linter reporta errores (warnings no bloquean).
- OWASP Dependency-Check detecta vulnerabilidad CRITICAL o HIGH sin supresión justificada.
- `npm audit` (frontend) detecta vulnerabilidad HIGH o CRITICAL.
- Mutation score < 70 % en `pagos`, `otp` o `reservas`.

### Quién consume este documento y para qué

| Agente | Uso |
|---|---|
| `tester-tdd` | Fuente única de verdad para frameworks, estructura, umbrales y ciclo TDD. No usa defaults propios. |
| `test-runner` | Comandos canónicos, umbrales, estructura de carpetas para tests de código ya implementado. |
| `api-tester` | Casos de seguridad OWASP, flujos críticos y forma de errores. |
| `verification-specialist` | Comandos de build/test/lint, umbrales de cobertura para su quality gate. |
| `reality-checker` | Comandos de validación, umbrales y flujos críticos para su veredicto PASS/NEEDS WORK. |
| `database-optimizer` | Estrategia híbrida H2/Testcontainers y restricciones de la capa de datos. |

---

## 2. Pirámide de Testing

### Distribución objetivo

```
              ▲
             / \
            /E2E\          5 %  — Playwright — flujos críticos completos
           /─────\
          / Integr \      15 %  — MockMvc + Testcontainers + WireMock
         /──────────\
        /    Unit    \    80 %  — JUnit 5 / Vitest — dominio, servicios, mappers
       ──────────────────
```

### Justificación de la distribución

La distribución estándar 80/15/5 se aplica con dos ajustes motivados por el dominio:

**1. Peso elevado en integración (15 % vs. el 10 % habitual):**  
PadelPro integra dos sistemas externos con contratos no triviales: Redsys (HMAC SHA-256 + 3DES-CBC) y Telegram Bot API (validación por header secreto). Ambas integraciones tienen lógica de firma que no se puede simular fielmente con mocks en memoria — necesitan WireMock levantado como servidor HTTP real para probar serialización, encoding Base64 y cálculo de firmas. Además, la restricción `EXCLUDE USING gist` es PostgreSQL-específica e imposible de probar en H2.

**2. E2E acotado a flujos de negocio críticos (5 %):**  
Los flujos E2E cubren únicamente los tres casos de uso principales (CU-01, CU-02, CU-03). No se usa E2E para flujos administrativos o de configuración — la cobertura de integración es suficiente para esos casos.

### Clasificación de tests por módulo

| Módulo | Unit | Integration | E2E |
|---|---|---|---|
| `reservas` | Entidades, domain service `ReservaDisponibilidadService`, mappers | Controller (MockMvc), repositorio (Testcontainers), solapamiento gist | CU-01, CU-02 |
| `pagos` | `Pago` entidad, `PagoCalculoService`, HMAC calculation | Webhook adapter (WireMock), repositorio (Testcontainers) | CU-03 |
| `usuarios` | `Usuario` entidad, `PasswordPolicyService`, `AuthApplicationService` | `AuthController` (MockMvc), lockout, refresh token | Login + logout |
| `mensajeria` | Parser de comandos Telegram, routing | `BotTelegramAdapter` (WireMock), secret header | — |
| `otp` | `Otp.estaExpirado()`, `estaUsado()`, generación SHA-256 | `OtpApplicationService` con repositorio real | OTP flow |
| `auditoria` | `AuditoriaEntry` (inmutable) | Escritura en BD real; retención | — |
| `shared` | `JwtProvider`, `PasswordPolicyService` | `SecurityConfig` RBAC | — |
| Frontend | Componentes, hooks, utils | MSW handlers | E2E Playwright |

---

## 3. Stack de Testing por Capa

### 3.1 Backend — Java 21 + Spring Boot 3.2

#### Tests unitarios

- **JUnit 5** (jupiter): incluido en `spring-boot-starter-test`. Sin necesidad de dependencia explícita.
- **AssertJ 3.x**: fluent assertions — preferido sobre `assertEquals`. Bundled con Spring Boot 3.2.
- **Mockito 5.x** con `MockitoSettings(strictness = STRICT_STUBS)` activado por defecto en Spring Boot Test. Prohíbe stubs sin usar — señal de test mal escrito.
- Los tests unitarios **no arrancan contexto Spring** — `@ExtendWith(MockitoExtension.class)` únicamente. Sin `@SpringBootTest` en tests de dominio.

```xml
<!-- pom.xml — spring-boot-starter-test ya incluye todo lo necesario -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

#### Tests de integración

```xml
<!-- Testcontainers — PostgreSQL 15 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<!-- WireMock — Redsys y Telegram -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.5.4</version>
    <scope>test</scope>
</dependency>
<!-- Spring Security Test — @WithMockUser, @WithUserDetails -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

#### Tests de arquitectura (ArchUnit)

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

**Reglas ArchUnit obligatorias por módulo:**

```java
// <module>/src/test/java/com/padelpro/<module>/arch/<Module>ArchTest.java
@AnalyzeClasses(packages = "com.padelpro.<module>")
class <Module>ArchTest {

    // 1. La capa domain NO depende de infrastructure
    @ArchTest
    static final ArchRule domain_no_depends_on_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    // 2. La capa domain NO depende de application
    @ArchTest
    static final ArchRule domain_no_depends_on_application =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..");

    // 3. Los controllers NO acceden directamente a repositories o JPA adapters
    @ArchTest
    static final ArchRule controllers_no_direct_repo_access =
        noClasses().that().resideInAPackage("..infrastructure.adapter.inbound..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure.adapter.outbound..");

    // 4. Los ApplicationService son @Transactional solo en application, no en domain
    @ArchTest
    static final ArchRule transactional_only_in_application =
        noClasses().that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(Transactional.class);

    // 5. Los ports (interfaces) viven solo en domain
    @ArchTest
    static final ArchRule ports_in_domain_only =
        classes().that().haveNameMatching(".*Port|.*UseCase")
            .should().resideInAPackage("..domain.port..");
}
```

#### Mutation testing (Pitest)

```xml
<!-- pom.xml — plugin en el módulo raíz o en cada módulo crítico -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.3</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
        </dependency>
    </dependencies>
    <configuration>
        <!-- Solo en módulos críticos -->
        <targetClasses>
            <param>com.padelpro.pagos.domain.*</param>
            <param>com.padelpro.pagos.application.*</param>
            <param>com.padelpro.otp.domain.*</param>
            <param>com.padelpro.otp.application.*</param>
            <param>com.padelpro.reservas.domain.*</param>
            <param>com.padelpro.reservas.application.*</param>
        </targetClasses>
        <targetTests>
            <param>com.padelpro.pagos.*</param>
            <param>com.padelpro.otp.*</param>
            <param>com.padelpro.reservas.*</param>
        </targetTests>
        <mutationThreshold>70</mutationThreshold>
        <coverageThreshold>80</coverageThreshold>
        <outputFormats>
            <outputFormat>HTML</outputFormat>
            <outputFormat>XML</outputFormat>
        </outputFormats>
        <timestampedReports>false</timestampedReports>
    </configuration>
</plugin>
```

#### Cobertura (JaCoCo)

```xml
<!-- pom.xml — maven-surefire + failsafe + jacoco -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- Surefire ejecuta *Test.java -->
        <includes>
            <include>**/*Test.java</include>
        </includes>
        <excludes>
            <exclude>**/*IT.java</exclude>
        </excludes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Failsafe ejecuta *IT.java -->
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
</plugin>

<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>prepare-agent-integration</id>
            <goals><goal>prepare-agent-integration</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
                <goal>report-integration</goal>
                <goal>merge</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.reporting.outputDirectory}/jacoco</outputDirectory>
                <formats>
                    <format>HTML</format>
                    <format>XML</format>
                </formats>
            </configuration>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <!-- Ver §4 para justificación de exclusiones -->
                    <exclude>**/config/**</exclude>
                    <exclude>**/*Application.class</exclude>
                    <exclude>**/dto/**</exclude>
                    <exclude>**/exception/*Exception.class</exclude>
                    <exclude>**/entity/*Entity.class</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

### 3.2 Frontend — React 18 + Vite + TypeScript

#### Tests unitarios y de componentes

**Vitest** en lugar de Jest — justificación: Vitest es el runner nativo del ecosistema Vite. Elimina la capa de transformación Jest/Babel (incompatibilidad ESM nativa), comparte la configuración de `vite.config.ts`, y es entre 2x y 5x más rápido en modo watch. Con TypeScript 5.x + ESM, Jest requiere configuración no trivial que Vitest evita por diseño.

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      thresholds: {
        lines: 75,
        branches: 70,
        functions: 75,
        statements: 75,
      },
      exclude: [
        'tests/**',
        '**/*.d.ts',
        '**/*.config.*',
        'src/main.tsx',
        'src/vite-env.d.ts',
      ],
    },
  },
});
```

```typescript
// tests/setup.ts
import '@testing-library/jest-dom';
import { server } from './mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

#### Mocking de API — MSW

```typescript
// tests/mocks/server.ts
import { setupServer } from 'msw/node';
import { authHandlers } from './handlers/auth.handlers';
import { reservasHandlers } from './handlers/reservas.handlers';
import { pagosHandlers } from './handlers/pagos.handlers';

export const server = setupServer(
  ...authHandlers,
  ...reservasHandlers,
  ...pagosHandlers,
);
```

Estructura de handlers:
```
frontend/tests/mocks/
├── server.ts
├── handlers/
│   ├── auth.handlers.ts      — POST /api/auth/login, /refresh, /logout
│   ├── reservas.handlers.ts  — GET/POST /api/reservas, POST /{id}/unirse
│   ├── pagos.handlers.ts     — POST /api/pagos/iniciar, GET /api/pagos
│   └── usuarios.handlers.ts  — GET/PATCH /api/usuarios/me
└── fixtures/
    ├── reserva.fixture.ts
    ├── usuario.fixture.ts
    └── pago.fixture.ts
```

#### E2E — Playwright

```typescript
// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Arrancar backend + frontend antes de E2E
  webServer: [
    {
      command: 'cd ../backend && mvn spring-boot:run -Dspring-boot.run.profiles=test',
      port: 8080,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev',
      port: 5173,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
```

---

### 3.3 Base de datos — Estrategia híbrida H2 / Testcontainers

#### DECISION CRITICA — Incompatibilidad H2 con `btree_gist`

> **H2 NO puede ejecutar la extensión `btree_gist` ni el constraint `EXCLUDE USING gist` de PostgreSQL.**  
> Esta restricción afecta directamente a la tabla `reservations` y a la lógica anti-solapamiento.

**Regla absoluta:**

| Tipo de test | Base de datos | Cuándo usarla |
|---|---|---|
| Tests unitarios de dominio | Sin BD (mocks) | `Reserva`, `Pago`, `Otp` — entidades puras |
| Tests unitarios de repositorios simples | H2 modo `MODE=PostgreSQL` | CRUD de `users`, `audit_log`, `notification_log`, `otp_codes` — sin constraints gist |
| Tests de integración con solapamiento | **Testcontainers PostgreSQL 15** | Todo lo que toque `reservations` — OBLIGATORIO |
| Tests de integración con Flyway | **Testcontainers PostgreSQL 15** | Verificar que las migraciones se aplican correctamente |
| Tests de integración de controllers | **Testcontainers PostgreSQL 15** | MockMvc + BD real = máxima fidelidad |

#### `application-test.yml` — H2 (tests unitarios de repositorios simples)

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:padelpro_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=true
    driver-class-name: org.h2.Driver
    username: sa
    password: ''
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false   # H2 no soporta las extensiones PostgreSQL de Flyway

logging:
  level:
    com.padelpro: WARN
    org.hibernate.SQL: OFF
```

#### `application-it.yml` — Testcontainers PostgreSQL 15 (integration tests)

```yaml
# src/test/resources/application-it.yml
spring:
  datasource:
    # La URL la inyecta @DynamicPropertySource del BaseIntegrationTest
    url: ${spring.datasource.url}
    username: ${spring.datasource.username}
    password: ${spring.datasource.password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    clean-on-validation-error: true   # Solo en tests; nunca en producción

app:
  jwt:
    secret: test-secret-key-padelpro-256-bits-minimum-length
    access-token-expiration-ms: 900000    # 15 min
    refresh-token-expiration-ms: 604800000 # 7 días
  telegram:
    webhook-secret: test-telegram-webhook-secret
    bot-token: test-telegram-bot-token
  redsys:
    secret-key: test-redsys-secret-key
    merchant-code: '999008881'
    terminal: '001'
```

#### Clase base de integración

```java
// src/test/java/com/padelpro/shared/BaseIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("padelpro_test")
            .withUsername("padelpro_test")
            .withPassword("padelpro_test")
            .withReuse(true);  // Reutiliza el contenedor entre clases IT del mismo build

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Limpieza entre tests cuando @Transactional no es suficiente
     * (e.g., tests de solapamiento que verifican el constraint gist).
     */
    @Sql("/sql/cleanup.sql")
    protected void cleanDatabase() { /* @Sql hace el trabajo */ }
}
```

#### WireMock — integraciones externas

```java
// src/test/java/com/padelpro/shared/BaseExternalServiceIT.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("it")
public abstract class BaseExternalServiceIT extends BaseIntegrationTest {

    @RegisterExtension
    static WireMockExtension telegramServer = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    @RegisterExtension
    static WireMockExtension redsysServer = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void configureMockServers(DynamicPropertyRegistry registry) {
        registry.add("app.telegram.api-url", () -> telegramServer.baseUrl());
        registry.add("app.redsys.tpv-url", () -> redsysServer.baseUrl());
    }
}
```

#### Limpieza entre tests de integración

```sql
-- src/test/resources/sql/cleanup.sql
-- Orden de borrado respeta las FKs
DELETE FROM notification_log;
DELETE FROM audit_log;
DELETE FROM otp_codes;
DELETE FROM participants;
DELETE FROM payments;
DELETE FROM reservations;
DELETE FROM refresh_tokens;
DELETE FROM system_config WHERE id = 1;
DELETE FROM users;

-- Reiniciar secuencias
ALTER SEQUENCE users_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_log_id_seq RESTART WITH 1;
ALTER SEQUENCE otp_codes_id_seq RESTART WITH 1;
ALTER SEQUENCE participants_id_seq RESTART WITH 1;
ALTER SEQUENCE refresh_tokens_id_seq RESTART WITH 1;
ALTER SEQUENCE notification_log_id_seq RESTART WITH 1;
```

---

### 3.4 CI/CD — GitHub Actions

#### Estructura de jobs

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [develop]
  push:
    branches: [develop, main]

jobs:
  build:
    name: Build
    timeout-minutes: 3
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Build (sin tests)
        run: mvn -B clean compile -DskipTests

  unit-tests:
    name: Unit Tests
    needs: build
    timeout-minutes: 2
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Run unit tests
        run: mvn -B test -Dtest="*Test" -DfailIfNoTests=false

  integration-tests:
    name: Integration Tests
    needs: build
    timeout-minutes: 8
    runs-on: ubuntu-latest
    services:
      # Docker socket disponible para Testcontainers
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Run integration tests
        run: mvn -B verify -Dtest="NONE" -DfailIfNoTests=false
        env:
          TESTCONTAINERS_RYUK_DISABLED: "true"

  frontend-tests:
    name: Frontend Tests
    needs: build
    timeout-minutes: 3
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install dependencies
        run: npm ci --prefix frontend
      - name: Run frontend tests
        run: npm run test:coverage --prefix frontend
      - name: Lint frontend
        run: npm run lint --prefix frontend

  quality-gate:
    name: Quality Gate
    needs: [unit-tests, integration-tests, frontend-tests]
    timeout-minutes: 1
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Check coverage thresholds
        run: mvn -B jacoco:check
      - name: OWASP Dependency Check
        run: mvn -B dependency-check:check -DfailBuildOnCVSS=7
      - name: Upload coverage report
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: '**/target/site/jacoco/'

  lint:
    name: Lint Backend
    needs: build
    timeout-minutes: 2
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Checkstyle
        run: mvn -B checkstyle:check
```

**Tiempos máximos por job:**

| Job | Máximo |
|---|---|
| build | 3 min |
| unit-tests | 2 min |
| integration-tests | 8 min |
| frontend-tests | 3 min |
| quality-gate | 1 min |
| lint | 2 min |

---

## 4. Umbrales de Cobertura

| Métrica | Mínimo | Objetivo | Cómo se mide | Quién verifica |
|---|---|---|---|---|
| Line coverage (backend) | 80 % | 85 % | JaCoCo `jacoco:check` | CI quality-gate job |
| Branch coverage (backend) | 75 % | 80 % | JaCoCo `jacoco:check` | CI quality-gate job |
| Line coverage (frontend) | 75 % | 80 % | Vitest c8 `--coverage` | CI frontend-tests job |
| Branch coverage (frontend) | 70 % | 75 % | Vitest c8 `--coverage` | CI frontend-tests job |
| Flujos críticos | **100 %** | 100 % | Revisión manual + JaCoCo XML | `verification-specialist` |
| Reglas de negocio (RN-xx) | **100 %** | 100 % | Tests dedicados por RN | `verification-specialist` |
| Mutation score (`pagos`, `otp`, `reservas`) | 70 % | 75 % | Pitest `mutationThreshold` | CI (fase manual) |
| Duplicación de código | < 3 % | < 2 % | Checkstyle / ESLint | CI lint job |

### Flujos críticos de PadelPro (cobertura 100 % no negociable)

Los siguientes flujos deben tener al menos un test de integración que los recorra de extremo a extremo:

1. **Autenticación completa**: login → JWT access token → uso del token → refresh → logout → refresh inválido (401).
2. **OTP Telegram**: generación (SHA-256 almacenado, TTL 10 min) → envío → validación correcta → OTP marcado `used=true` → 4.º intento fallido → OTP invalidado.
3. **Creación de reserva (web)**: disponibilidad → INSERT reserva + pago + participante (atómico) → publicación en Telegram (WireMock).
4. **Anti-solapamiento de reservas**: dos peticiones concurrentes al mismo slot → exactamente una falla con 409. Test de concurrencia con Testcontainers PostgreSQL 15 (constraint `gist`).
5. **Cancelación de reserva**: dentro del plazo → `CANCELLED` + pago `CANCELLED`; fuera del plazo → reserva `CANCELLED`, pago `PENDING`.
6. **Unirse a reserva**: hueco disponible → participante añadido; hueco completo → 409; usuario ya participante → 409.
7. **Pago Redsys — inicio**: cálculo de importe en backend → firma HMAC SHA-256 → URL del TPV generada; PAN/CVV nunca en logs.
8. **Pago Redsys — webhook**: HMAC válido → pago `PAID`; HMAC inválido → ignorado (200 sin cambio de estado); webhook duplicado → idempotente (segunda llamada no cambia nada).
9. **RBAC — endpoints admin**: `USER` accede a `/api/admin/**` → 403; `ADMIN` accede → 200.
10. **RBAC — ownership**: `USER` B accede a reserva de `USER` A → 403 (no 200, no 404).
11. **Bot Telegram**: webhook sin `X-Telegram-Bot-Api-Secret-Token` → 403; webhook con secret correcto → 200.
12. **RGPD — anonimización**: `DELETE /api/admin/usuarios/{id}` → `first_name=ANONIMIZADO`, `phone=NULL`, `email=deleted_{id}@...`, entrada `audit_log.action=USER_ANONYMIZED`, `refresh_tokens` revocados.
13. **Bloqueo de cuenta**: 10 intentos fallidos en 10 min → cuenta `LOCKED`; desbloqueo automático tras 15 min.
14. **Account lockout OTP**: 3 intentos fallidos → OTP `used=true` (invalidado automáticamente).

### Exclusiones de cobertura (justificadas)

| Patrón excluido | Justificación |
|---|---|
| `**/config/**` | Clases de configuración Spring sin lógica de negocio testeable |
| `**/*Application.class` | Clase main de Spring Boot — arranque de contexto sin lógica |
| `**/dto/**` | Records/DTOs con cero lógica — solo fields y constructores |
| `**/*Exception.class` | Jerarquía de excepciones sin lógica propia |
| `**/entity/*Entity.class` | Entidades JPA — la lógica está en el domain model, no en la entidad de infraestructura |
| `**/SwaggerConfig.class`, `**/OpenApiConfig.class` | Configuración de documentación sin lógica |

---

## 5. Naming y Estructura de Tests

### 5.1 Backend (Java)

#### Naming de clases

| Tipo | Sufijo | Ejemplo |
|---|---|---|
| Test unitario | `Test` | `ReservaTest.java`, `OtpApplicationServiceTest.java` |
| Test de integración | `IT` | `ReservaControllerIT.java`, `PagoWebhookAdapterIT.java` |
| Test de arquitectura | `ArchTest` | `ReservasArchTest.java`, `SharedArchTest.java` |
| Test de concurrencia | `ConcurrencyIT` | `ReservaSolapamientoConcurrencyIT.java` |

#### Naming de métodos

Formato: `should_<comportamiento_esperado>_when_<condicion>`

```java
// Unitarios
void should_lanzar_excepcion_when_reserva_solapada()
void should_calcular_importe_correcto_when_duracion_90_minutos()
void should_marcar_otp_usado_when_validacion_correcta()

// Integración
void should_return_409_when_slot_ya_reservado()
void should_return_403_when_user_role_accede_admin_endpoint()
void should_return_401_when_token_no_presente()
void should_invalidar_otp_when_tercer_intento_fallido()
```

#### Maven Surefire vs. Failsafe

- **Surefire** (`mvn test`): ejecuta `*Test.java` — tests unitarios.
- **Failsafe** (`mvn verify`): ejecuta `*IT.java` — tests de integración. Requiere Docker para Testcontainers.

### 5.2 Frontend (TypeScript/React)

#### Naming de archivos

| Tipo | Convención | Ejemplo |
|---|---|---|
| Test de componente | `<Component>.test.tsx` collocated | `ReservaForm.test.tsx` |
| Test de hook | `<hook>.test.ts` collocated | `useAuth.test.ts` |
| Test de util | `<util>.test.ts` collocated | `formatFecha.test.ts` |
| E2E | `tests/e2e/<flow>.spec.ts` | `reserva-flow.spec.ts` |

### 5.3 Patrón AAA — obligatorio en todos los tests

```java
@Test
void should_return_409_when_slot_ya_reservado() throws Exception {
    // Arrange
    UsuarioEntity usuario = usuarioRepository.save(UsuarioBuilder.unUsuarioActivo().build());
    reservaRepository.save(ReservaBuilder.unaReserva()
        .conFecha(LocalDate.now().plusDays(1))
        .conHoraInicio(LocalTime.of(18, 0))
        .conDuracion(60)
        .conOwner(usuario)
        .build());
    String token = generarTokenJwt(usuario);

    // Act
    ResultActions result = mockMvc.perform(post("/api/reservas")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(
            CrearReservaRequest.builder()
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(18, 0))
                .duracionMinutos(60)
                .build()
        )));

    // Assert
    result.andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SLOT_NO_DISPONIBLE"));
}
```

---

## 6. Builders y Datos de Test

### 6.1 Backend — Builders por entidad

Ubicación: `src/test/java/com/padelpro/<module>/builders/`

Convención: valor válido por defecto + métodos `con<Campo>(...)`.

```java
// builders/UsuarioBuilder.java
public class UsuarioBuilder {
    private String login = "testuser";
    private String passwordHash = "$2a$12$test-bcrypt-hash";
    private String firstName = "Test";
    private String lastName = "Usuario";
    private String phone = "+34600000001";
    private String email = "test@padelpro.local";
    private UserStatus status = UserStatus.ACTIVE;
    private UserRole role = UserRole.USER;

    public static UsuarioBuilder unUsuarioActivo() { return new UsuarioBuilder(); }
    public static UsuarioBuilder unAdmin() {
        return new UsuarioBuilder().conRole(UserRole.ADMIN);
    }

    public UsuarioBuilder conLogin(String login) { this.login = login; return this; }
    public UsuarioBuilder conEmail(String email) { this.email = email; return this; }
    public UsuarioBuilder conRole(UserRole role) { this.role = role; return this; }
    public UsuarioBuilder conStatus(UserStatus status) { this.status = status; return this; }
    public UsuarioBuilder conTelegramChatId(String chatId) {
        this.telegramChatId = chatId; return this;
    }

    public UsuarioEntity build() {
        UsuarioEntity e = new UsuarioEntity();
        e.setLogin(login);
        e.setPasswordHash(passwordHash);
        // ... setters
        return e;
    }
}
```

**Builders obligatorios:**

| Builder | Módulo | Campos clave |
|---|---|---|
| `UsuarioBuilder` | `usuarios` | login, email, phone, role, status, telegramChatId |
| `ReservaBuilder` | `reservas` | fecha, horaInicio, duracionMinutos, estado, canal, owner |
| `ParticipanteBuilder` | `reservas` | reservaId, userId, externalName, slotPosition, isOwner |
| `PagoBuilder` | `pagos` | reservaId, amount, method, status, redsysOrderId |
| `OtpBuilder` | `otp` | userId, code, type, expiresAt, used |
| `PistaBuilder` | `shared` | — (si se añade entidad pista en versiones futuras) |

### 6.2 Frontend — Factories

```typescript
// tests/factories/reserva.factory.ts
import { Reserva, ReservaStatus } from '../../src/types/reserva';

export const buildReserva = (overrides: Partial<Reserva> = {}): Reserva => ({
  id: '550e8400-e29b-41d4-a716-446655440000',
  fecha: '2026-06-01',
  horaInicio: '18:00',
  horaFin: '19:30',
  duracionMinutos: 90,
  status: ReservaStatus.CONFIRMED,
  canal: 'WEB',
  participantes: [],
  ...overrides,
});
```

### 6.3 Fixtures — respuestas de sistemas externos

```
src/test/resources/fixtures/
├── redsys/
│   ├── webhook-pago-aprobado.json          — Ds_Response=0000
│   ├── webhook-pago-rechazado.json         — Ds_Response=0190
│   ├── webhook-firma-invalida.json         — Ds_Signature manipulado
│   └── webhook-duplicado.json             — mismo Ds_Merchant_Order
└── telegram/
    ├── update-reserva-comando.json         — mensaje "reserva de pista ..."
    ├── update-cancelacion-comando.json     — mensaje "cancelacion reserva ..."
    ├── update-vincular-otp.json            — mensaje "/vincular XXXXXX"
    ├── update-sin-secret.json              — request sin header X-Telegram-Bot-Api-Secret-Token
    └── update-chat-id-desconocido.json    — chat_id no vinculado a ningún usuario
```

**Prohibido:** Datos de test hardcodeados dispersos en tests individuales. Todo dato de test pasa por builders o factories.

---

## 7. Reglas de Mocking

### Regla 1 — Solo dependencias externas al SUT

```
✅ Mockear: ReservaRepositoryPort (cuando el SUT es ReservaApplicationService)
✅ Mockear: MensajeriaPort (puerto de salida)
✅ Mockear: TelegramClientAdapter en tests unitarios de MensajeriaApplicationService
❌ NUNCA: mockear ReservaApplicationService cuando es el SUT
❌ NUNCA: mockear el Repositorio JPA en tests de integración (usar Testcontainers)
```

### Regla 2 — Strict stubs obligatorios

```java
// En tests unitarios Spring Boot 3.2 — ya activado por defecto
// Si se usa @ExtendWith(MockitoExtension.class) directamente:
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ReservaApplicationServiceTest { ... }
```

Cualquier stub definido pero no invocado falla el test — señal de test mal diseñado o redundante.

### Regla 3 — WireMock para Redsys y Telegram en integration tests

```java
// En BaseExternalServiceIT — WireMock como servidor HTTP real
telegramServer.stubFor(WireMock.post("/bot{token}/sendMessage")
    .willReturn(WireMock.ok()
        .withHeader("Content-Type", "application/json")
        .withBodyFile("telegram/send-message-ok.json")));
```

### Regla 4 — MSW en frontend

```typescript
// tests/mocks/handlers/reservas.handlers.ts
import { http, HttpResponse } from 'msw';

export const reservasHandlers = [
  http.get('/api/reservas/disponibles', () =>
    HttpResponse.json([buildReserva({ status: ReservaStatus.CONFIRMED })]),
  ),
  http.post('/api/reservas', () =>
    HttpResponse.json(buildReserva(), { status: 201 }),
  ),
];
```

### Regla 5 — Verificación de interacciones solo cuando forma parte del contrato

```java
// ✅ Verificar que el servicio de auditoría registró la acción — es parte del contrato
verify(auditoriaPort).registrar(
    eq("RESERVATION_CREATED"),
    eq("RESERVATION"),
    eq(reservaId.toString()),
    any()
);

// ❌ NO verificar detalles de implementación interna (cuántas veces se llama save, etc.)
```

---

## 8. Tests por Área Crítica

### 8.1 Autenticación

```java
// UsuarioControllerIT.java — tests de seguridad mínimos obligatorios

@Test
void should_return_200_and_jwt_when_credenciales_validas() { ... }

@Test
void should_return_401_when_password_incorrecta() { ... }

@Test
void should_not_leak_user_existence_in_401_response() {
    // El mensaje de error 401 es idéntico para usuario inexistente y password incorrecta
}

@Test
void should_return_423_when_cuenta_bloqueada_por_10_intentos() { ... }

@Test
void should_return_401_when_refresh_token_revocado() { ... }

@Test
void should_return_401_when_refresh_token_expirado() { ... }

@Test
void should_invalidar_refresh_token_on_logout() { ... }

@Test
void should_return_401_on_refresh_after_logout() { ... }

@Test
void should_return_401_when_no_auth_header() throws Exception {
    mockMvc.perform(get("/api/usuarios/me"))
           .andExpect(status().isUnauthorized());
}

@Test
void should_return_403_when_usuario_pendiente_intenta_login() { ... }
```

#### OTP Telegram

```java
// OtpApplicationServiceIT.java

@Test
void should_almacenar_otp_como_sha256_no_en_claro() {
    // Arrange: generar OTP
    // Act: buscar en BD
    // Assert: el campo code en BD no es igual al código generado en claro
    //         (es el hash SHA-256)
}

@Test
void should_expirar_otp_despues_de_10_minutos() { ... }

@Test
void should_invalidar_otp_en_cuarto_intento_fallido() { ... }

@Test
void should_marcar_used_true_despues_de_validacion_correcta() { ... }

@Test
void should_rechazar_otp_ya_usado() { ... }

@Test
void should_rechazar_otp_de_otro_usuario() { ... }
```

### 8.2 RBAC — Todos los endpoints

Cada endpoint del OpenAPI debe tener al menos estos dos tests de seguridad:

```java
// Template RBAC — aplicar a cada endpoint protegido

@Test
void should_return_401_when_no_autenticado() throws Exception {
    mockMvc.perform(get("/api/<endpoint>"))
           .andExpect(status().isUnauthorized());
}

@Test
void should_return_403_when_rol_insuficiente() throws Exception {
    mockMvc.perform(get("/api/admin/<endpoint>")
            .with(user("testuser").roles("USER")))
           .andExpect(status().isForbidden());
}
```

**Tabla de cobertura RBAC mínima:**

| Endpoint | PUBLIC | USER | ADMIN |
|---|---|---|---|
| `POST /auth/login` | ✅ 200 | — | — |
| `POST /auth/register` | ✅ 201 | — | — |
| `POST /auth/refresh` | ✅ 200 | — | — |
| `POST /auth/logout` | ✅ 401 anon | ✅ 200 | ✅ 200 |
| `GET /usuarios/me` | ✅ 401 anon | ✅ 200 | ✅ 200 |
| `GET /admin/usuarios` | ✅ 401 anon | ✅ 403 | ✅ 200 |
| `POST /admin/usuarios` | ✅ 401 anon | ✅ 403 | ✅ 201 |
| `DELETE /admin/usuarios/{id}` | ✅ 401 anon | ✅ 403 | ✅ 200 |
| `GET /reservas/disponibles` | ✅ 401 anon | ✅ 200 | ✅ 200 |
| `POST /reservas` | ✅ 401 anon | ✅ 201 | ✅ 201 |
| `DELETE /reservas/{id}` | ✅ 401 anon | ✅ solo owner | ✅ 200 |
| `GET /admin/reservas` | ✅ 401 anon | ✅ 403 | ✅ 200 |
| `POST /pagos/iniciar` | ✅ 401 anon | ✅ solo owner | ✅ 200 |
| `POST /admin/pagos/{id}/efectivo` | ✅ 401 anon | ✅ 403 | ✅ 200 |
| `POST /otp/verificar` | ✅ 401 anon | ✅ 200 | ✅ 200 |

### 8.3 Redsys — Pagos

```java
// PagoWebhookAdapterIT.java

@Test
void should_procesar_pago_when_hmac_valido_y_respuesta_aprobada() {
    // Arrange: crear reserva y pago PENDING
    // Act: POST /api/pagos/webhook con payload firmado correctamente
    // Assert: payment.status == PAID, audit_log contiene PAYMENT_CONFIRMED
}

@Test
void should_ignorar_webhook_when_hmac_invalido() {
    // Assert: payment.status sigue en PENDING, no se crea audit_log de PAYMENT_CONFIRMED
    // El endpoint responde 200 (nunca revelar el fallo a Redsys)
}

@Test
void should_ser_idempotente_when_webhook_duplicado() {
    // Primer webhook → PAID
    // Segundo webhook idéntico → sigue PAID, no hay duplicado
}

@Test
void should_pan_cvv_nunca_aparecer_en_audit_log() {
    // Después de procesar webhook, buscar en audit_log.details
    // No debe contener patrones de PAN (16 dígitos) ni CVV
}

@Test
void should_calcular_importe_en_backend_no_aceptar_de_frontend() {
    // El importe en payments.amount es price_per_hour × duration / 60
    // No debe ser manipulable desde el request del frontend
}
```

### 8.4 Reservas — Anti-solapamiento

```java
// ReservaSolapamientoConcurrencyIT.java (requiere Testcontainers — NO H2)

@Test
@Transactional(propagation = Propagation.NOT_SUPPORTED)  // Sin transacción padre
void should_exactamente_una_reserva_succeed_when_peticiones_concurrentes_mismo_slot()
    throws InterruptedException, ExecutionException {

    // Arrange
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);
    UsuarioEntity[] usuarios = crearNUsuarios(threadCount);

    // Act
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        futures.add(executor.submit(() -> {
            latch.countDown();
            latch.await();
            try {
                mockMvc.perform(post("/api/reservas")
                    .header("Authorization", "Bearer " + tokens[idx])
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(buildReservaRequestJson(fecha, LocalTime.of(18, 0), 60)));
                // Si llega aquí sin excepción = 201 o 409
                // Contar según status
            } catch (Exception e) { throw new RuntimeException(e); }
        }));
    }

    // Assert
    // Exactamente 1 éxito, threadCount-1 conflictos
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
}

@Test
void should_constraint_gist_rechazar_solapamiento_via_sql_directo()
    throws Exception {
    // Test de la segunda barrera — directo a la BD sin pasar por la aplicación
    // INSERT que viola el constraint gist debe lanzar excepción de PostgreSQL
}
```

### 8.5 Bot Telegram

```java
// BotTelegramAdapterIT.java

@Test
void should_return_403_when_secret_token_invalido() throws Exception {
    mockMvc.perform(post("/api/bot/telegram")
            .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loadFixture("telegram/update-reserva-comando.json")))
           .andExpect(status().isForbidden());
}

@Test
void should_return_200_when_secret_token_valido() throws Exception {
    // Verificar que el webhook es procesado
}

@Test
void should_responder_error_when_chat_id_no_vinculado() throws Exception {
    // El bot responde al chat indicando que debe vincular la cuenta
}

@Test
void should_vinculacion_exitosa_when_otp_tipo_telegram_link_valido() { ... }
```

### 8.6 Base de datos — Constraints

```java
// RepositorioConstraintsIT.java (Testcontainers)

@Test
void should_lanzar_excepcion_when_email_duplicado() { ... }

@Test
void should_lanzar_excepcion_when_redsys_order_id_duplicado() { ... }

@Test
void should_lanzar_excepcion_when_telegram_chat_id_duplicado() { ... }

@Test
void should_set_null_participant_user_id_when_user_deleted_logically() {
    // El user se inactiva (soft-delete), el participant.user_id queda NULL (SET NULL)
}

@Test
void should_restringir_borrado_reserva_cuando_tiene_pago() {
    // ON DELETE RESTRICT en payments.reservation_id
    // No se puede borrar una reserva que tiene pago
}

@Test
void should_flyway_aplicar_todas_las_migraciones_en_orden() {
    // Verifica que V1 a V12 se aplican sin error en un contenedor limpio
}
```

---

## 9. Tests de Cumplimiento RGPD

```java
// RgpdComplianceIT.java

@Test
void should_respuesta_error_no_exponer_datos_personales() throws Exception {
    // Intentar login con usuario inexistente
    // La respuesta 401 NO debe contener email, nombre, teléfono
    mockMvc.perform(post("/api/auth/login")
            .content("{\"login\":\"inexistente\",\"password\":\"wrong\"}"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message").doesNotContain("@"))
           .andExpect(jsonPath("$.message").doesNotContain("+34"));
}

@Test
void should_logs_no_contener_passwords_ni_tokens() {
    // Capturar log output durante un login
    // Verificar que no aparece la password ni el JWT
    // Usar ListAppender de Logback en tests
}

@Test
void should_logs_no_contener_otp_en_claro() {
    // Durante generación de OTP, verificar que el código no aparece en logs
}

@Test
void should_anonimizar_usuario_correctamente_when_derecho_al_olvido() throws Exception {
    // Arrange: usuario activo con telegram_chat_id vinculado
    // Act: DELETE /api/admin/usuarios/{id}
    // Assert:
    //   - users.first_name = 'ANONIMIZADO'
    //   - users.last_name = 'ANONIMIZADO'
    //   - users.phone = NULL
    //   - users.email = 'deleted_{id}@padelpro.invalid'
    //   - users.telegram_chat_id = NULL
    //   - participants.external_name = 'ANONIMIZADO' donde user_id = {id}
    //   - audit_log contiene action='USER_ANONYMIZED'
    //   - refresh_tokens.revoked = true para todos los tokens del usuario
    //   - otp_codes.used = true para todos los OTPs activos del usuario
}

@Test
void should_retener_reservas_y_pagos_aunque_usuario_anonimizado() {
    // reservations y payments NO se borran con la anonimización
}

@Test
void should_pan_cvv_nunca_en_audit_log_details() {
    // Después de procesar cualquier webhook Redsys, buscar patrones de tarjeta
    // en audit_log.details — nunca debe aparecer
}
```

---

## 10. Política de Flaky Tests

### Definición y detección

Un test es **flaky** si pasa en menos del 95 % de las ejecuciones sin cambios en el código bajo test.

**Causas frecuentes en PadelPro:**
- `Thread.sleep()` para esperar procesamiento asíncrono → usar `Awaitility`.
- Tests de concurrencia sin barreras (`CountDownLatch`) → corregir sincronización.
- Estado compartido entre tests sin cleanup → añadir `@Sql("/sql/cleanup.sql")` o `@Transactional`.
- WireMock con stubs solapados entre tests → resetear en `@AfterEach`.

### Política de cuarentena

```java
// Test identificado como flaky: añadir tag y documentar
@Test
@Tag("flaky")
@Disabled("Flaky desde 2026-06-01. Issue: #123. Deadline: 2026-06-08.")
void should_ejemplo_test_flaky() { ... }
```

| Etapa | Acción |
|---|---|
| Detección | CI marca test como flaky si falla sin cambios de código |
| Cuarentena | `@Tag("flaky")` — excluido de la suite de merge |
| Deadline | 7 días calendario desde detección |
| Si no se corrige | Issue GitHub `type:bug priority:must area:<modulo>` |

### Awaitility — sustituto de Thread.sleep

```java
// ❌ PROHIBIDO
Thread.sleep(2000);

// ✅ OBLIGATORIO
Awaitility.await()
    .atMost(5, TimeUnit.SECONDS)
    .pollInterval(100, TimeUnit.MILLISECONDS)
    .until(() -> notificationLogRepository.findByUserId(userId).stream()
        .anyMatch(n -> n.getStatus() == NotificationStatus.SENT));
```

---

## 11. Tiempos Máximos de Ejecución

| Suite | Tiempo máximo | Acción si se supera |
|---|---|---|
| Tests unitarios backend | 2 minutos | Investigar tests lentos; `Thread.sleep` prohibido |
| Tests integración backend | 8 minutos | Revisar cleanup SQL; habilitar `withReuse(true)` en Testcontainers |
| Tests unitarios frontend | 90 segundos | Reducir renders innecesarios; revisar MSW handlers |
| Suite E2E completa | 5 minutos | Revisar `webServer` config; reducir tests E2E a solo flujos críticos |
| Test individual | 5 segundos | Requiere comentario justificando la duración en el método de test |

---

## 12. Quality Gate — Criterios de Fallo Automático

La build de CI falla **sin excepción** si se cumple cualquiera de estas condiciones:

| Condición | Herramienta | Job |
|---|---|---|
| Cualquier test unitario falla | Maven Surefire / Vitest | `unit-tests` / `frontend-tests` |
| Cualquier test de integración falla | Maven Failsafe | `integration-tests` |
| Cobertura de líneas < 80 % (backend) | JaCoCo `jacoco:check` | `quality-gate` |
| Cobertura de branches < 75 % (backend) | JaCoCo `jacoco:check` | `quality-gate` |
| Cobertura de líneas < 75 % (frontend) | Vitest c8 | `frontend-tests` |
| Linter reporta error (no warning) | Checkstyle / ESLint | `lint` |
| CVE CRITICAL o HIGH sin supresión | OWASP Dependency-Check | `quality-gate` |
| npm audit HIGH o CRITICAL | npm audit | `frontend-tests` |
| Mutation score < 70 % en módulos críticos | Pitest | Manual (fase CI avanzada) |
| Test E2E crítico falla | Playwright | `e2e-tests` (job separado) |

**Nunca** bajar los umbrales para hacer pasar la build. Si la cobertura está por debajo del umbral, añadir los tests que faltan.

---

## 13. Comandos Canónicos

> Estos son los comandos exactos que TODOS los agentes (`verification-specialist`, `reality-checker`, `api-tester`, `tester-tdd`) deben usar. No hay variaciones.

| Propósito | Comando | Output |
|---|---|---|
| **Build sin tests** | `mvn -B clean compile -DskipTests` | `BUILD SUCCESS` o errores de compilación |
| **Unit tests backend** | `mvn -B test -Dtest="*Test" -DfailIfNoTests=false` | Surefire report en `target/surefire-reports/` |
| **Integration tests backend** | `mvn -B verify -Dtest="NONE" -DfailIfNoTests=false` | Failsafe report en `target/failsafe-reports/` |
| **Todos los tests backend** | `mvn -B verify` | Unit + integration + JaCoCo check |
| **Generar reporte de cobertura** | `mvn -B jacoco:report` | HTML en `target/site/jacoco/index.html` |
| **Verificar umbrales de cobertura** | `mvn -B jacoco:check` | Falla si < 80 % líneas / < 75 % branches |
| **Tests de arquitectura** | `mvn -B test -Dtest="*ArchTest"` | ArchUnit violations o PASS |
| **Mutation testing** | `mvn -B org.pitest:pitest-maven:mutationCoverage` | HTML en `target/pit-reports/` |
| **Lint backend (Checkstyle)** | `mvn -B checkstyle:check` | Violations o `BUILD SUCCESS` |
| **OWASP Dependency Check** | `mvn -B dependency-check:check -DfailBuildOnCVSS=7` | Reporte HTML + fallo si HIGH/CRITICAL |
| **Unit tests frontend** | `npm run test --prefix frontend` | Vitest output |
| **Unit tests frontend con cobertura** | `npm run test:coverage --prefix frontend` | Coverage report en `frontend/coverage/` |
| **Lint frontend** | `npm run lint --prefix frontend` | ESLint output |
| **E2E (requiere servidor levantado)** | `npm run test:e2e --prefix frontend` | Playwright report en `playwright-report/` |
| **Todos los tests locales** | `mvn -B verify && npm run test:coverage --prefix frontend` | Suite completa |
| **Solo tests de un módulo** | `mvn -B test -pl reservas -Dtest="*Test"` | Tests del módulo `reservas` |
| **Arrancar servidor para api-tester** | `mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=test &` | Servidor en `http://localhost:8080` |

**Variables de entorno requeridas para tests de integración:**

```bash
# No requeridas — Testcontainers inyecta sus propias credenciales vía @DynamicPropertySource
# El perfil 'it' usa los valores inyectados dinámicamente
```

---

## 14. Estructura de Carpetas

### Backend (Maven multi-módulo)

```
backend/
├── pom.xml                                    ← parent pom
│
├── reservas/
│   ├── src/
│   │   ├── main/java/com/padelpro/reservas/
│   │   └── test/
│   │       ├── java/com/padelpro/reservas/
│   │       │   ├── unit/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── ReservaTest.java
│   │       │   │   │   └── ParticipanteTest.java
│   │       │   │   └── application/
│   │       │   │       └── ReservaApplicationServiceTest.java
│   │       │   ├── integration/
│   │       │   │   ├── ReservaControllerIT.java
│   │       │   │   ├── ReservaRepositorioIT.java
│   │       │   │   └── ReservaSolapamientoConcurrencyIT.java
│   │       │   ├── arch/
│   │       │   │   └── ReservasArchTest.java
│   │       │   └── builders/
│   │       │       ├── ReservaBuilder.java
│   │       │       └── ParticipanteBuilder.java
│   │       └── resources/
│   │           ├── application-test.yml       ← H2 para unit repos simples
│   │           ├── application-it.yml         ← Testcontainers PostgreSQL 15
│   │           ├── sql/
│   │           │   └── cleanup.sql
│   │           └── fixtures/
│   │               ├── redsys/
│   │               └── telegram/
│
├── pagos/
│   └── src/test/
│       ├── java/com/padelpro/pagos/
│       │   ├── unit/
│       │   │   ├── domain/
│       │   │   │   └── PagoTest.java
│       │   │   └── application/
│       │   │       └── PagoApplicationServiceTest.java
│       │   ├── integration/
│       │   │   ├── PagoControllerIT.java
│       │   │   ├── PagoWebhookAdapterIT.java   ← WireMock Redsys
│       │   │   └── PagoRepositorioIT.java
│       │   ├── arch/
│       │   │   └── PagosArchTest.java
│       │   └── builders/
│       │       └── PagoBuilder.java
│       └── resources/
│           ├── application-test.yml
│           ├── application-it.yml
│           └── fixtures/redsys/
│
├── usuarios/
│   └── src/test/
│       ├── java/com/padelpro/usuarios/
│       │   ├── unit/
│       │   │   ├── domain/
│       │   │   │   └── UsuarioTest.java
│       │   │   └── application/
│       │   │       ├── AuthApplicationServiceTest.java
│       │   │       └── UsuarioApplicationServiceTest.java
│       │   ├── integration/
│       │   │   ├── AuthControllerIT.java       ← login, refresh, logout, lockout
│       │   │   ├── UsuarioControllerIT.java
│       │   │   └── AdminUsuarioControllerIT.java
│       │   ├── arch/
│       │   │   └── UsuariosArchTest.java
│       │   └── builders/
│       │       └── UsuarioBuilder.java
│       └── resources/
│           ├── application-test.yml
│           └── application-it.yml
│
├── mensajeria/
│   └── src/test/
│       ├── java/com/padelpro/mensajeria/
│       │   ├── unit/
│       │   │   └── application/
│       │   │       └── MensajeriaApplicationServiceTest.java
│       │   └── integration/
│       │       └── BotTelegramAdapterIT.java   ← WireMock Telegram
│       └── resources/
│           └── fixtures/telegram/
│
├── otp/
│   └── src/test/
│       ├── java/com/padelpro/otp/
│       │   ├── unit/
│       │   │   ├── domain/
│       │   │   │   └── OtpTest.java
│       │   │   └── application/
│       │   │       └── OtpApplicationServiceTest.java
│       │   ├── integration/
│       │   │   └── OtpApplicationServiceIT.java
│       │   └── builders/
│       │       └── OtpBuilder.java
│       └── resources/
│
├── auditoria/
│   └── src/test/
│       └── java/com/padelpro/auditoria/
│           ├── unit/
│           │   └── domain/
│           │       └── AuditoriaEntryTest.java
│           └── integration/
│               └── AuditoriaApplicationServiceIT.java
│
└── shared/
    └── src/test/
        ├── java/com/padelpro/shared/
        │   ├── BaseIntegrationTest.java        ← clase base con Testcontainers
        │   ├── BaseExternalServiceIT.java      ← clase base con WireMock
        │   ├── security/
        │   │   └── JwtProviderTest.java
        │   ├── integration/
        │   │   ├── SecurityConfigIT.java       ← RBAC global
        │   │   └── RgpdComplianceIT.java       ← RGPD
        │   └── builders/
        │       └── (builders compartidos si aplica)
        └── resources/
            └── sql/
                └── cleanup.sql
```

### Frontend

```
frontend/
├── src/
│   ├── components/
│   │   └── ReservaForm/
│   │       ├── ReservaForm.tsx
│   │       └── ReservaForm.test.tsx           ← collocated
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   └── useAuth.test.ts                    ← collocated
│   └── utils/
│       ├── formatFecha.ts
│       └── formatFecha.test.ts                ← collocated
│
└── tests/
    ├── e2e/
    │   ├── reserva-flow.spec.ts               ← CU-01 + CU-02
    │   ├── pago-flow.spec.ts                  ← CU-03
    │   └── auth-flow.spec.ts                  ← Login, logout, RBAC
    ├── mocks/
    │   ├── server.ts
    │   └── handlers/
    │       ├── auth.handlers.ts
    │       ├── reservas.handlers.ts
    │       ├── pagos.handlers.ts
    │       └── usuarios.handlers.ts
    ├── factories/
    │   ├── reserva.factory.ts
    │   ├── usuario.factory.ts
    │   └── pago.factory.ts
    ├── fixtures/
    │   ├── reserva-list.json
    │   └── usuario-profile.json
    └── setup.ts
```

---

## 15. Guía de Onboarding para Desarrolladores

### "Cómo ejecutar todos los tests con un solo comando"

```bash
# Desde la raíz del repositorio
# Requiere: Java 21, Maven 3.9+, Node.js 20+, Docker (para Testcontainers)

# Backend completo + frontend
mvn -B verify && npm run test:coverage --prefix frontend

# Solo backend
mvn -B verify

# Solo frontend
npm run test:coverage --prefix frontend

# Solo E2E (requiere backend + frontend levantados)
npm run test:e2e --prefix frontend
```

### "Cómo añadir un test unitario paso a paso"

1. Identifica la clase bajo test (SUT): e.g., `OtpApplicationService`.
2. Crea el archivo: `otp/src/test/java/com/padelpro/otp/unit/application/OtpApplicationServiceTest.java`.
3. Añade la anotación de extensión:
   ```java
   @ExtendWith(MockitoExtension.class)
   @MockitoSettings(strictness = Strictness.STRICT_STUBS)
   class OtpApplicationServiceTest { ... }
   ```
4. Usa un builder para el objeto de test: `OtpBuilder.unOtpActivo().build()`.
5. Sigue el patrón AAA con comentarios visibles.
6. Ejecuta solo ese test: `mvn -B test -Dtest="OtpApplicationServiceTest" -pl otp`.

### "Cómo añadir un test de integración con Testcontainers"

1. Extiende `BaseIntegrationTest` (ya tiene el contenedor PostgreSQL 15 configurado).
2. El nombre de la clase termina en `IT`: e.g., `ReservaControllerIT`.
3. Usa el perfil `it` (aplicado automáticamente en `BaseIntegrationTest`).
4. Añade `@Sql("/sql/cleanup.sql")` en `@BeforeEach` para aislar el estado.
5. Ejecuta la suite de integración: `mvn -B verify -Dtest="NONE" -pl reservas`.
6. Si el test toca la restricción gist de solapamiento, usa `Propagation.NOT_SUPPORTED` para que el constraint de PostgreSQL dispare correctamente.

### "Cómo depurar un test fallido en CI"

1. Descarga el artefacto `coverage-report` de la ejecución de CI para ver el reporte HTML de JaCoCo.
2. Para tests de integración fallidos, comprueba los logs de Testcontainers:
   ```bash
   # Logs del contenedor PostgreSQL durante los tests
   mvn -B verify -Dtest="NONE" -Dtestcontainers.log-level=DEBUG
   ```
3. Para tests de WireMock fallidos, habilitar logging de requests:
   ```java
   wiremockServer.setGlobalFixedDelay(0);
   System.setProperty("wiremock.verbose", "true");
   ```
4. Para tests de concurrencia intermitentes, ejecutar 10 veces:
   ```bash
   mvn -B test -Dtest="ReservaSolapamientoConcurrencyIT" -Dsurefire.rerunFailingTestsCount=10
   ```
5. Si el test pasa en local y falla en CI, verificar que Docker está disponible (Testcontainers requiere Docker socket).

---

## 16. Roadmap de Adopción

El repositorio parte de cero — sin código de producción, sin tests. Las fases son secuenciales.

### Fase 0 — Scaffolding (Sprint 1, Ticket TICKET-001)
- [ ] Generar `docs/TESTING-STRATEGY.md` (este documento) con `test-strategist`.
- [ ] Aprobar la estrategia con el propietario del producto.
- [ ] Añadir dependencias de test en todos los `pom.xml` de módulos.
- [ ] Crear `BaseIntegrationTest.java` con Testcontainers.
- [ ] Crear `BaseExternalServiceIT.java` con WireMock.
- [ ] Crear `sql/cleanup.sql`.
- [ ] Configurar JaCoCo con umbrales en el `pom.xml` padre.
- [ ] Configurar GitHub Actions CI con los jobs del §3.4.
- [ ] Crear `vitest.config.ts` con c8 y umbrales.
- [ ] Configurar MSW server en `tests/setup.ts`.

### Fase 1 — Primer ciclo TDD (Sprint 1)
- [ ] **US-003 — Auto-registro**: primera historia de menor complejidad.
- [ ] Ciclo Red-Green-Refactor para `POST /api/auth/register`.
- [ ] Tests unitarios: `UsuarioBuilder`, `AuthApplicationServiceTest`, `PasswordPolicyServiceTest`.
- [ ] Tests de integración: `AuthControllerIT.should_return_201_when_registro_valido`.
- [ ] Test RBAC: `should_return_401_when_no_autenticado`.

### Fase 2 — Auth y RBAC (Sprint 1-2)
- [ ] **US-001** — Login web + JWT.
- [ ] **US-002** — Refresh token + logout.
- [ ] **US-004** — Vinculación Telegram + OTP tipo TELEGRAM_LINK.
- [ ] **US-005** — Toggle estado usuario (ADMIN).
- [ ] **US-008** — Confirmar OTP para operaciones críticas.
- [ ] Cobertura completa de la tabla RBAC del §8.2.
- [ ] Tests de bloqueo de cuenta (10 intentos → LOCKED).

### Fase 3 — Reservas (Sprint 2-3)
- [ ] **US-006** — Crear reserva (web).
- [ ] **US-007** — Disponibilidad de pista.
- [ ] **US-009** — Cancelar reserva (dentro y fuera del plazo).
- [ ] **US-010** — Unirse a reserva (hueco libre y hueco completo).
- [ ] **US-011** — Reserva vía Telegram + OTP.
- [ ] Test de concurrencia `ReservaSolapamientoConcurrencyIT`.
- [ ] Test del constraint gist a nivel SQL.

### Fase 4 — Pagos Redsys (Sprint 3)
- [ ] **US-014** — Inicio de pago Redsys (HMAC SHA-256).
- [ ] **US-015** — Webhook Redsys (aprobado, rechazado, inválido, duplicado).
- [ ] **US-016** — Pago en efectivo (ADMIN).
- [ ] Tests PCI-lite: verificar que PAN/CVV nunca aparecen.

### Fase 5 — E2E críticos (Sprint 3-4)
- [ ] `reserva-flow.spec.ts`: CU-01 (crear) + CU-02 (unirse).
- [ ] `pago-flow.spec.ts`: CU-03 (pago online).
- [ ] `auth-flow.spec.ts`: login → reserva → logout → refresh inválido.

### Fase 6 — Mutation testing (Post Sprint 4)
- [ ] Configurar Pitest en módulos `pagos`, `otp`, `reservas`.
- [ ] Alcanzar mutation score ≥ 70 %.
- [ ] Añadir tests adicionales si mutation score < 70 %.

---

## 17. Decisiones Abiertas

Las siguientes decisiones requieren confirmación del propietario del producto antes de que `tester-tdd` pueda iniciar la Fase 1:

| # | Decisión | Impacto en testing | Acción requerida |
|---|---|---|---|
| **D-TEST-01** | **OTP almacenado como SHA-256 vs. en claro** (referencia D-SEC-02 de `security-design.md`): el campo `otp_codes.code` es `VARCHAR(6)`. Si se decide almacenar como SHA-256, el test `should_almacenar_otp_como_sha256_no_en_claro` requiere verificar que el valor en BD es un hash de 64 caracteres, no 6 dígitos. | Cambia la lógica del builder `OtpBuilder` y los assertions del test de almacenamiento. | Confirmar decisión D-SEC-02 en `security-design.md`. |
| **D-TEST-02** | **Confirmación OTP en reservas web** (referencia P2 de `data-model.md`): ¿La reserva creada desde web requiere OTP para pasar de `PENDING_CONFIRMATION` a `CONFIRMED`? Si no, el test de creación de reserva web debe verificar status `CONFIRMED` directamente. | El flujo de test de CU-01 web cambia según si hay o no paso de OTP intermedio. | Validar RN-07 para canal WEB con el dueño del producto. |
| **D-TEST-03** | **Lifetime del access token** (referencia D-SEC-07 de `security-design.md`): 15 min (este documento) vs. 8h (README). Los tests de expiración de token deben usar el valor correcto en el `application-it.yml`. | Los tests que verifican expiración dependen del valor configurado. | Confirmar con D-SEC-07 el valor definitivo. |
| **D-TEST-04** | **Rotación de refresh tokens** (D-SEC-01): si se implementa en v1.0, añadir test `should_invalidar_refresh_antiguo_when_usado` y `should_detectar_reutilizacion_de_token_robado`. Si no se implementa, no son necesarios. | Añade 2 tests de integración al flujo de auth si se implementa. | Confirmar con D-SEC-01. |
| **D-TEST-05** | **Portabilidad de datos RGPD** (`GET /api/usuarios/me/exportar`, D-SEC-03): si se implementa, añadir test que verifica que la exportación incluye todos los datos propios y no incluye datos de otros usuarios. | Añade 1-2 tests de integración al controlador de usuarios. | Confirmar con D-SEC-03. |
| **D-TEST-06** | **Horario de apertura del club** (P4 de `data-model.md`): si se añaden columnas `opening_time`/`closing_time` a `system_config`, añadir tests que verifiquen que no se puede crear una reserva fuera del horario. | Añade casos de test en `ReservaControllerIT` y en `ReservaDisponibilidadService`. | Confirmar si es requisito v1.0 o v2. |
| **D-TEST-07** | **Módulo frontend — alcance de E2E**: ¿los flujos E2E de Playwright corren contra el backend completo (con Testcontainers PostgreSQL 15) o contra el backend con H2 y WireMock? Recomendación: backend de test con perfil `it` + Testcontainers para máxima fidelidad. | Afecta a la configuración `webServer` de `playwright.config.ts` y al entorno de CI E2E. | Confirmar antes de escribir los E2E de la Fase 5. |
