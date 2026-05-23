Actúa como test-strategist (o backend-architect en modo "test architect" si
el agente especializado no existe aún). Tu tarea es generar
`docs/TESTING-STRATEGY.md` desde cero para el proyecto PadelPro.

## Paso 0 — Contexto obligatorio
Antes de escribir nada, lee en este orden:
1. `docs/PROJECT.md` — stack, módulos, convenciones, identidad del bot.
2. `README.md` — especificación funcional y técnica.
3. `docs/data-model.md` — entidades, FKs, constraints, datos sensibles.
   Si no existe, detente.
4. `docs/security-design.md` — autenticación, roles, matriz RBAC, OWASP.
   Si no existe, detente.
5. `docs/openapi.yaml` — si existe, lee endpoints y contratos. Si no, no
   bloquees: puedes generar la estrategia y luego se afinará.
6. `backlog.md` — Épicas, Features, US y Tickets para identificar flujos
   críticos.
7. Los agentes que van a CONSUMIR este documento:
   `.claude/agents/tester-tdd.md`, `.claude/agents/api-tester.md`,
   `.claude/agents/verification-specialist.md`,
   `.claude/agents/reality-checker.md`,
   `.claude/agents/database-optimizer.md`.
   Léelos para alinear umbrales y comandos.

Si alguno de los documentos obligatorios falta, detente y avisa. No
inventes contenido.

## Objetivo
Producir la estrategia de testing completa y vinculante de PadelPro. Este
documento es ley para todos los agentes que prueben código. Debe ser:
- **Ejecutable**: cada decisión incluye el comando o configuración concreta
  que la materializa.
- **Verificable**: cada umbral debe poder medirse automáticamente.
- **Coherente con el stack**: Java 21 + Spring Boot 3.2, React 18 + Vite +
  TypeScript, PostgreSQL 15, GitHub Actions.
- **Coherente con el dominio**: pasarela Redsys, bot Telegram con OTP,
  RGPD, RBAC con los roles definidos en `docs/security-design.md`.

## Estructura obligatoria del documento

### 1. Resumen ejecutivo
- Objetivo de la estrategia en 5-7 líneas.
- Stack de testing elegido (frameworks, librerías, versiones).
- Umbrales de cobertura globales (líneas, branches, flujos críticos).
- Política de quality gate (build falla si...).
- Quién consume este documento y para qué.

### 2. Pirámide de testing
Distribución objetivo y por qué:
- **Unit tests (~80%)**: lógica de dominio, servicios, mappers, validadores,
  hooks, utils, componentes puros.
- **Integration tests (~15%)**: controladores con MockMvc/WebTestClient,
  repositorios contra BD real (Testcontainers), seguridad
  (`@WithMockUser`/`@WithUserDetails`), servicios externos (WireMock).
- **E2E tests (~5%)**: flujos críticos completos (Playwright o Cypress).

Si justificas otra distribución por el dominio, hazlo explícito.

### 3. Stack de testing por capa

#### Backend (Java 21 + Spring Boot 3.2)
- **Unit**: JUnit 5, AssertJ, Mockito 5. Justifica versiones.
- **Integration**: Spring Boot Test, MockMvc, Testcontainers (PostgreSQL 15
  para fidelidad con producción), WireMock para servicios externos
  (Redsys, Telegram).
- **Arquitectura**: ArchUnit para validar fronteras hexagonales (dominio
  no depende de infraestructura, etc.). Define reglas concretas.
- **Mutación (opcional pero recomendado)**: PIT/Pitest sobre módulos
  críticos (pagos, auth). Umbral mutation score y módulos cubiertos.
- **Cobertura**: JaCoCo. Configuración Maven (plugin, excludes,
  thresholds), formato de reporte (HTML + XML para CI).

#### Frontend (React 18 + Vite + TypeScript)
- **Unit / componentes**: Vitest + React Testing Library. Justifica
  Vitest sobre Jest (alineación con Vite).
- **Mock de API**: MSW (Mock Service Worker). Estructura de handlers en
  `tests/mocks/`.
- **E2E**: Playwright (preferido sobre Cypress por velocidad y soporte
  multi-navegador). Configuración base, navegadores objetivo (Chromium
  mínimo).
- **Cobertura**: c8 / istanbul vía Vitest. Umbrales y excludes.

#### Base de datos
Estrategia híbrida:
- **H2 en modo PostgreSQL** para tests unitarios rápidos de repositorios.
  Justifica si vale o si todo va con Testcontainers.
- **Testcontainers + PostgreSQL 15** para tests de integración con
  fidelidad de producción.
- Configuración: `application-test.yml` (H2) vs `application-it.yml`
  (Testcontainers).
- Estrategia de migraciones en tests: Flyway corre en cada arranque o
  schema-per-test. Decide y justifica.
- Limpieza entre tests: `@Transactional` con rollback, o `@Sql`
  cleanup scripts. Define convención.

#### CI/CD (GitHub Actions)
- Estructura de workflow: jobs `build`, `unit-tests`, `integration-tests`,
  `coverage`, `quality-gate`, `lint`.
- Triggers: PR a `develop`, push a `develop`, push a `main`.
- Caché de dependencias (Maven `~/.m2`, npm `~/.npm`).
- Servicios necesarios (Docker para Testcontainers).
- Tiempos máximos esperados por job. Si CI tarda más de X, falla.
- Publicación de reportes: artifacts de cobertura, comentario en PR con
  resumen.

### 4. Umbrales de cobertura

Tabla `Métrica | Mínimo | Objetivo | Cómo se mide | Quién la verifica`:
- Cobertura de líneas: mín 80%, objetivo 85%.
- Cobertura de branches: mín 75%, objetivo 80%.
- Cobertura de flujos críticos: 100% (no negociable).
- Cobertura de RN-xx (reglas de negocio críticas): 100%.
- Mutation score (módulos críticos): mín 70%.
- Duplicación de código: <3%.

Define qué se entiende por "flujo crítico" en PadelPro:
- Autenticación (login, refresh, OTP Telegram).
- Reservas (creación, cancelación, conflicto de horario).
- Pagos Redsys (iniciación, callback HMAC, idempotencia, reconciliación).
- RBAC: cada combinación rol × endpoint protegido tiene test.
- Bot Telegram: vinculación de cuenta y OTP.

Excludes legítimos (justifica cada uno): configuración Spring, DTOs sin
lógica, código generado, `main()` de la app.

### 5. Convenciones de naming y estructura

#### Java
- Unit: `[ClaseSUT]Test.java`. Métodos: `should_<esperado>_when_<condición>`
  o `[Método]_[Escenario]_[Resultado]`.
- Integration: sufijo `IT`. Ejemplo: `ReservaControllerIT.java`,
  `PagoServiceIT.java`.
- ArchUnit: `arch/` package, clases `<Capa>ArchTest.java`.
- Maven Surefire ejecuta `*Test`, Failsafe ejecuta `*IT`.

#### TypeScript / React
- Componentes: `<Componente>.test.tsx` colocado junto al componente.
- Hooks: `<hook>.test.ts`.
- Utils: `<util>.test.ts`.
- E2E: `tests/e2e/<flujo>.spec.ts`.

#### Patrón AAA / Given-When-Then
Visible y comentado en el test:
```java
// Given
var reserva = ReservaBuilder.default().build();
// When
var resultado = reservaService.crear(reserva);
// Then
assertThat(resultado.estado()).isEqualTo(CONFIRMADA);
```

### 6. Builders y test data

- **Backend**: builders por entidad en `src/test/java/.../builders/`.
  Cada builder con valores por defecto válidos y métodos
  `with<Campo>(...)`. Ejemplo: `ReservaBuilder`, `UsuarioBuilder`.
- **Frontend**: factories en `tests/factories/`. Mismo patrón.
- **Fixtures pesadas** (JSON de respuesta Redsys, payloads Telegram):
  carpeta `tests/fixtures/` con un fichero por escenario.
- **Prohibido**: datos de test dispersos hardcoded en cada test.

### 7. Mocking — reglas
- Mockear SOLO dependencias externas al SUT (repos, clientes HTTP,
  servicios externos).
- NUNCA mockear el SUT.
- WireMock para Redsys y Telegram en tests de integración.
- MSW para API en tests de frontend.
- Mockito strict-stubs activado (Spring Boot lo hace por defecto en JUnit 5).
- Verificación de interacciones SOLO cuando la interacción es parte del
  contrato (no para "asegurar que se llamó al log").

### 8. Tests por área crítica

#### Autenticación
- Login con credenciales válidas → 200 + JWT.
- Login con credenciales inválidas → 401, sin filtrar si usuario existe.
- Bloqueo tras N intentos fallidos (según `docs/security-design.md`).
- Refresh token: válido, expirado, revocado.
- Logout: token invalidado en próximas peticiones.
- OTP Telegram: generación, expiración (5 min), un solo uso.

#### RBAC
Tabla `Endpoint | Rol esperado | Test de PASS | Test de FAIL (otro rol)`.
Cada combinación con test propio. Mínimo: ADMIN, MANAGER_CLUB, JUGADOR,
INVITADO (ajusta a roles reales de `docs/security-design.md`).

#### Pagos Redsys
- Generación de firma HMAC SHA-256 correcta.
- Verificación de firma del callback.
- Idempotencia: webhook duplicado no crea segundo pago.
- Protección replay (timestamp/nonce).
- Reconciliación: pago iniciado sin callback recibido tras X minutos.
- Estados de pago: transiciones válidas e inválidas.

#### Reservas
- Creación con franja libre → OK.
- Conflicto de horario (otra reserva activa) → 409.
- Cancelación dentro de plazo → OK + reembolso si pagado.
- Cancelación fuera de plazo → 422 con regla aplicada.
- Lista filtrada por usuario, pista, rango de fechas.

#### Bot Telegram
- Vinculación: usuario solicita OTP, valida, queda vinculado.
- Comandos en grupo: solo usuarios vinculados con rol adecuado.
- Webhook con secret incorrecto → ignorado.

#### Base de datos
- Constraints únicos disparan error en lugar de duplicar.
- ON DELETE configurado correctamente (CASCADE / RESTRICT / SET NULL).
- Índices se usan: tests con `EXPLAIN` sobre queries críticas (opcional
  pero recomendado).

### 9. Cumplimiento normativo (RGPD)
- Test que verifica que las respuestas de error NO exponen datos
  personales.
- Test que verifica que los logs NO incluyen passwords, tokens, PAN, CVV.
- Test del endpoint de "derecho al olvido": el usuario eliminado se
  anonimiza correctamente y queda trazable en auditoría.
- Test de retención: jobs programados eliminan/anonimizan datos según
  política.

### 10. Política de flaky tests
- Detección: marcador automático en CI si un test pasa intermitentemente.
- Cuarentena: si un test es flaky, se mueve a un grupo `@Tag("flaky")` y
  NO bloquea el merge, pero se reporta en un panel.
- Plazo máximo en cuarentena: 7 días. Si no se arregla, se borra o se
  reabre como bug `priority:must` en GitHub.
- Cero tolerancia con `Thread.sleep`. Usar Awaitility con timeout y
  polling explícitos.

### 11. Tiempos máximos
- Suite unitaria completa backend: <2 minutos.
- Suite de integración backend: <8 minutos.
- Suite unitaria frontend: <90 segundos.
- E2E completa: <5 minutos.
- Si un test individual tarda >5 segundos, requiere justificación
  documentada en el test.

### 12. Quality gate (criterios de fallo automático)
La build falla si:
- Algún test falla.
- Cobertura cae por debajo de umbrales.
- Cobertura de flujo crítico < 100%.
- Linter reporta error (no warning).
- Vulnerabilidad CRITICAL o HIGH detectada por Dependency-Check / npm audit.
- Mutation score (módulos críticos) < 70%.

### 13. Comandos canónicos
Tabla con los comandos que TODOS los agentes deben usar:
- Ejecutar tests unitarios backend: `mvn -f ... test`
- Ejecutar tests de integración backend: `mvn -f ... verify`
- Generar cobertura: `mvn ... jacoco:report`
- Ejecutar tests frontend: `npm --prefix frontend run test`
- E2E: `npm --prefix frontend run test:e2e`
- Linter backend: `mvn ... checkstyle:check` (o el que se decida)
- Linter frontend: `npm --prefix frontend run lint`

Estos comandos son los que `verification-specialist`, `reality-checker`,
`api-tester` y `test-runner` ejecutan. Deben funcionar tal cual.

### 14. Estructura de carpetas

Plasma el árbol esperado:
backend/
src/
main/...
test/
java/
.../unit/
.../integration/
.../arch/
.../builders/
resources/
application-test.yml
application-it.yml
fixtures/
frontend/
src/
.../Componente.test.tsx
tests/
e2e/
mocks/
factories/
fixtures/
### 15. Onboarding del desarrollador
- "Cómo lanzo todos los tests en local en una sola orden."
- "Cómo añado un test unitario nuevo paso a paso."
- "Cómo añado un test de integración con Testcontainers."
- "Cómo depuro un test fallido en CI."

### 16. Roadmap de adopción

Si el repo arranca de cero, define fases:
- **Fase 0**: Andamiaje (esta estrategia + configuración base).
- **Fase 1**: Primer ciclo TDD sobre la US más pequeña.
- **Fase 2**: Cobertura de auth y RBAC.
- **Fase 3**: Cobertura de pagos Redsys.
- **Fase 4**: E2E de flujos críticos.
- **Fase 5**: Mutation testing en módulos críticos.

### 17. Decisiones abiertas
Lista al final con todo lo que requiera input mío o de negocio antes de
arrancar el TDD.

## Reglas de construcción

1. **Coherencia con los agentes existentes.** Los comandos de la sección 13
   deben ser EXACTAMENTE los que ejecutan los agentes. Si un agente usa
   `mvn -f $BACKEND_DIR/pom.xml test`, este documento usa eso, no
   variantes.
2. **Coherencia con `docs/PROJECT.md`.** Mismo stack, mismas convenciones
   de naming de ramas, misma identidad del bot.
3. **Coherencia con `docs/data-model.md` y `docs/security-design.md`.**
   Los flujos críticos cubiertos deben coincidir con entidades y
   reglas declaradas allí.
4. **Verificable y ejecutable.** Cada umbral con su comando. Cada decisión
   con su configuración.
5. **No copies plantillas genéricas.** Adapta a PadelPro: pádel, reservas,
   Redsys, Telegram, RGPD español. Si una sección no aplica al dominio,
   omítela y justifícalo.
6. **Mantenibilidad sobre exhaustividad.** Prefiero 70 reglas claras y
   ejecutadas que 200 reglas teóricas. Si una decisión añade fricción
   sin valor, déjala fuera y márcala como "considerada y descartada".

## Entregable

Crea `docs/TESTING-STRATEGY.md` siguiendo la estructura anterior.

Cuando termines, devuélveme un resumen de 15-20 líneas con:
- Stack de testing consolidado.
- Umbrales de cobertura finales.
- Número de flujos críticos identificados y cubiertos.
- Comandos canónicos definidos (lista breve).
- Decisiones abiertas que requieren mi OK.

Espera mi validación antes de proponer commit. Tras mi OK, `tester-tdd`
podrá ejecutar su Fase 2 y arrancar el TDD del proyecto.