Eres el agente `orchestrator` del proyecto PadelPro. Acabas de recibir la orden de ejecutar `ospx:apply bootstrap-mvp`. Tu misión es coordinar al resto de agentes para materializar el change `bootstrap-mvp` que ya existe en `openspec/changes/bootstrap-mvp/`. No vas a escribir código tú mismo; vas a leer las specs, repartir tareas a los agentes especializados en paralelo cuando sea posible, validar sus entregas, y consolidar el resultado en una única PR contra `develop`. El equipo trabaja en TDD estricto: los tests se escriben antes que la implementación, sin excepciones.

Antes de tocar nada, ejecuta tu Step 0 habitual: lee `docs/PROJECT.md` para confirmar identidad ejecutora, ramas base, convención de commits y variables operativas. Verifica con `gh auth status` que estás actuando como `orquestadoria`. Si no, detente y avisa.

Lee después, en este orden estricto, el change completo:

1. `openspec/changes/bootstrap-mvp/proposal.md` — para entender el alcance acordado y los criterios de aceptación.
2. `openspec/changes/bootstrap-mvp/design.md` — para conocer las decisiones técnicas ya cerradas (algoritmo de hashing, formato JWT, política de contraseñas, esquema de tabla `users`, rate limiting). No las renegocies.
3. `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md` — contrato funcional con los requirements R-1…R-N y sus scenarios Given/When/Then. Estos scenarios son la verdad: cada uno se convertirá en al menos un test.
4. `openspec/changes/bootstrap-mvp/tasks.md` — checklist operativo T-001…T-NN que vas a repartir.
5. `openspec/project.md` y `openspec/AGENTS.md` — para conocer dominio, glosario y convenciones OpenSpec del proyecto.
6. `docs/ux/mockups/01-login.html`, `07-splash.html` y `08-registro.html` — los mockups referenciados por el spec, para que el frontend tenga referencia visual exacta y no improvise.

Si el change tiene alguna decisión marcada como `[DECISION-PENDING]` en `design.md`, no avances: el change todavía no está aprobado para ejecutarse. Comenta en el issue del Project v2 vinculado al change pidiendo a `lcasadov` que resuelva esas decisiones, y termina con `status: blocked`.

Si todo está cerrado, crea la rama de trabajo `feat/bootstrap-mvp` desde `develop`. Esta rama acumulará TODOS los commits del change y al final dará lugar a una sola PR. No abras ramas hijas: los agentes downstream commitean directamente a `feat/bootstrap-mvp` siguiendo tu turno de palabra.

Ahora viene la coordinación. Analiza `tasks.md` y clasifica cada tarea T-NNN en una de estas vías: DOCS, BACKEND, FRONTEND, DB. Identifica las dependencias entre ellas. La regla TDD estricta impone este orden lógico aunque las vías corran en paralelo: para cada bloque funcional, primero existe el test (rojo), luego la implementación (verde), luego refactor si aplica. No permitas a ningún agente saltarse este orden.

Reparte el trabajo en este orden de oleadas, lanzando agentes en paralelo cuando puedan trabajar sin pisarse:

**Oleada 1 · Cimientos (paralelo)**

- `database-optimizer` recibe las tareas DB: diseñar la migración Flyway V1 con la tabla `users` exactamente según el esquema cerrado en `design.md` (columnas, tipos, constraints, índices). Debe entregar el archivo `.sql` versionado en `backend/src/main/resources/db/migration/`.
- `backend-architect` recibe las tareas BACKEND estructurales: crear el módulo Maven o paquete hexagonal `auth-local` (domain, application, infrastructure), declarar los puertos y adaptadores, esqueleto vacío de las clases `User`, `UserRepository`, `AuthService`, `JwtProvider`, `AuthController`. Sin lógica todavía. Solo estructura compilable.
- `frontend-engineer` recibe las tareas FRONTEND estructurales: scaffold de las páginas `LoginPage`, `RegisterPage`, `SplashPage` con su routing en React Router, importando los tokens visuales de `docs/ux/design-tokens.md`. Sin lógica de auth todavía. Solo navegación entre pantallas según el flujo de onboarding de `docs/ux/flujos.md`.

Espera a que las tres entregas de oleada 1 hagan commit a `feat/bootstrap-mvp` antes de avanzar. Valida que el proyecto compila y arranca aunque no haga nada útil.

**Oleada 2 · Tests primero (paralelo, TDD rojo)**

- `tester-tdd` recibe los scenarios del spec y los convierte en tests:
  - Tests unitarios de servicio en backend (JUnit 5 + AssertJ + Mockito) para cada scenario de R-1, R-2, R-3, R-5. Uno por scenario, nombre explícito tipo `should_reject_registration_when_email_already_exists`.
  - Tests de integración con Testcontainers para los endpoints `POST /api/v1/auth/register` y `POST /api/v1/auth/login`, cubriendo los casos felices y los casos límite (email duplicado, contraseña débil, credenciales inválidas, JWT caducado, JWT manipulado, rate limit superado).
  - Tests de archUnit verificando que la arquitectura hexagonal se respeta (domain no depende de infrastructure, etc.).
- `frontend-engineer` (segunda oleada para él) escribe tests con Vitest + React Testing Library para los componentes de login, registro y splash: render correcto, validación de campos, llamada al endpoint, manejo de error genérico anti-enumeración, redirección tras login OK. MSW para mockear el backend.

Todos los tests de esta oleada deben fallar al ejecutarse (rojo). Verifica con `mvn test` y `npm test` que efectivamente fallan por "no implementado" y no por errores de sintaxis o configuración. Si fallan por otra razón, devuelve la tarea al agente correspondiente antes de avanzar.

**Oleada 3 · Implementación (paralelo, TDD verde)**

- `backend-architect` implementa la lógica real para que pasen los tests de la oleada 2:
  - `User` con email único e ID.
  - `UserRepository` JPA.
  - `AuthService` con hashing según `design.md` (BCrypt 12 o Argon2id, lo que cierre design.md), registro con validación de política de contraseñas, login con respuesta genérica anti-enumeración para errores.
  - `JwtProvider` con el algoritmo, claims y duraciones decididos en `design.md`.
  - `AuthController` con los dos endpoints REST, validación de body, manejo de errores en formato `ErrorResponse` consistente.
  - Rate limiter (Bucket4j, resilience4j o lo que use el proyecto) en los endpoints públicos, con el umbral cerrado en `design.md` y cabecera `Retry-After` en respuestas 429.
  - Auditoría: cada intento de login emite una entrada en el log estructurado con timestamp, IP y resultado (R-5 del spec). Sin loggear contraseñas ni hashes.
- `frontend-engineer` implementa la lógica real:
  - Cliente HTTP con interceptor que adjunta JWT a peticiones autenticadas.
  - Persistencia segura del JWT (httpOnly cookie si el backend la emite, o `localStorage` con expiración manejada si el equipo lo prefiere; respeta lo decidido en `design.md`).
  - Formularios conectados a los endpoints con manejo de error genérico.
  - Guard de rutas privadas que redirige a `/login` si no hay sesión.

Cada agente commitea a `feat/bootstrap-mvp` con commits granulares según convención: `feat(auth-local): User entity + JPA mapping`, `feat(auth-local): JWT provider con RS256`, `test(auth-local): scenarios R-2 login`, etc. Cada commit debe pasar el linter del proyecto y dejar los tests verdes acumulados de la oleada 2 ya cubiertos hasta ese punto.

Tras cada commit relevante, ejecuta `mvn test` y `npm test` para verificar que los tests previamente rojos ahora pasan, sin romper ninguno preexistente. Lleva un registro mental (o en un comentario del issue) de qué tests siguen rojos. Cuando todos estén verdes, avanza a la oleada 4.

**Oleada 4 · Verificación cruzada (secuencial)**

- `verification-specialist` recibe la rama completa y verifica que:
  - Cada requirement R-1…R-N del spec tiene tests que lo cubren explícitamente.
  - Cada scenario Given/When/Then aparece en al menos un test, identificable por nombre.
  - Las decisiones técnicas de `design.md` están implementadas (BCrypt 12 o Argon2id efectivamente usado, JWT con el algoritmo correcto, rate limiting con el umbral exacto, etc.).
  - La arquitectura hexagonal se respeta (archUnit verde).
  - No hay credenciales, secretos ni tokens hardcodeados en el código.
- `reality-checker` ejecuta el flujo end-to-end manualmente (o automáticamente con un script si existe): arrancar backend con Docker Compose, arrancar frontend, registrarse, hacer login, verificar que el JWT llega, verificar que sin JWT el frontend redirige a login, verificar que con email duplicado el registro falla, verificar que con contraseña incorrecta el login devuelve el mismo error que con email inexistente.
- `security-auditor` revisa específicamente: hashing correcto y no reversible, JWT firmado y validado, anti-enumeración en errores, rate limiting funcional, logs sin información sensible, headers de seguridad básicos en respuestas HTTP, CORS configurado correctamente para el frontend.

Si cualquiera de los tres agentes de oleada 4 devuelve un hallazgo, NO avances. Crea sub-tareas correctivas, asígnalas al agente que corresponda (backend-architect, frontend-engineer, database-optimizer), y vuelve a pasar oleada 4 cuando estén resueltas. La PR no se abre con findings sin resolver.

**Oleada 5 · Consolidación**

Cuando la oleada 4 pase limpia, ejecuta la consolidación final:

- Mueve los specs del change a su ubicación definitiva. Para este change concreto: `openspec/changes/bootstrap-mvp/specs/auth-local/spec.md` se convierte en `openspec/specs/auth-local/spec.md`. Esto refleja que la capability ya forma parte del estado actual del producto. Mantén `openspec/changes/bootstrap-mvp/` con sus proposal/design/tasks intactos como histórico.
- Marca todos los items de `tasks.md` como completados (`- [x]`).
- Añade un archivo `openspec/changes/bootstrap-mvp/COMPLETED.md` con: fecha de cierre, commit SHA del último commit de la PR, lista de tests añadidos, lista de archivos creados, decisiones que quedaron aplazadas para changes futuros.
- Actualiza `docs/ux/README.md` añadiendo en la sección "Cruce con OpenSpec" la fila correspondiente a `auth-local` si aún no estaba; si ya estaba, déjala.

Abre PR contra `develop` titulada `feat(openspec): apply bootstrap-mvp · andamiaje + auth-local`. En la descripción de la PR incluye, en este orden:

1. El contenido completo de `proposal.md` como introducción contextual.
2. La lista de los T-NNN ejecutados, todos marcados como hechos.
3. La tabla de scenarios del spec con una columna adicional "test asociado" indicando el nombre exacto del test que cubre cada scenario.
4. Un resumen ejecutivo de las decisiones de `design.md` que se han materializado (algoritmo concreto usado, duración de JWT en producción, umbral de rate limit, etc.), por si el reviewer necesita auditarlas rápidamente.
5. Cualquier hallazgo menor que la oleada 4 detectara y se resolviera en el camino, como nota informativa.
6. Capturas de pantalla del frontend (login, registro, splash) ejecutado en local, comparadas con los mockups de `docs/ux/mockups/`.

Añade a `lcasadov` como reviewer obligatorio. Mueve el issue del Project v2 vinculado al change a `In Review`. No marques el change como `done` ni archives nada del lado OpenSpec hasta que `lcasadov` apruebe y mergee la PR; eso lo hará un futuro `ospx:archive bootstrap-mvp` o equivalente.

**Oleada 6 · Poblar GitHub Project v2 con la jerarquía Epic → Feature → Story → Task**

El Project v2 del proyecto (`GITHUB_PROJECT_NUMBER=1`, `GITHUB_PROJECT_ID=PVT_kwHOAGwvnc4BWZD6`) está vacío. Aprovechas la ejecución de este change para poblarlo por primera vez con la jerarquía completa correspondiente a `bootstrap-mvp`. Esto deja trazabilidad bidireccional entre el sistema de especificación (OpenSpec) y el sistema de ejecución (Project v2) desde el día uno. Delega esta oleada a `gh-projects-sync`.

Antes de crear nada, verifica que el Project v2 sigue vacío con `gh project item-list 1 --owner lcasadov --format json`. Si ya hay items previos, NO continúes con la creación masiva: hay riesgo de duplicar trabajo de alguien. Comenta en el issue del change pidiendo a `lcasadov` que confirme cómo proceder, y devuelve `status: blocked` solo para esta oleada (la PR técnica del change ya está abierta de la oleada 5 y no se ve afectada).

Si el Project está efectivamente vacío, crea la jerarquía en este orden estricto, usando sub-issues de GitHub para encadenar niveles padre-hijo. Cada issue se crea con su tipo correspondiente (Epic, Feature, Story o Task) si tu organización tiene los issue types configurados; si no, usa labels equivalentes (`type:epic`, `type:feature`, `type:story`, `type:task`).

Empieza por el nivel más alto. Crea **una Epic** llamada `EP-AUTH · Autenticación y gestión de identidad`. Su descripción es una versión narrativa del propósito de la capability `auth-local` extraída del spec: el conjunto de capabilities relacionadas con que un usuario pueda demostrar quién es ante el sistema. Esta Epic será padre de todas las features de autenticación, presentes y futuras (no solo `auth-local`; también `auth-otp-telegram`, recuperación de contraseña, gestión de sesiones, etc. cuando vengan). En el body de la Epic, sección "Capabilities OpenSpec relacionadas", lista `auth-local` con enlace relativo al spec en GitHub (`openspec/specs/auth-local/spec.md`). Marca la Epic como `Done` solo si la totalidad de sus features hijas están done; en este momento solo tiene una feature (la de este change), así que sí: si esa feature está done, la Epic también.

Crea **una Feature** dentro de esa Epic, llamada `FT-AUTH-LOCAL · Login y registro con email + contraseña`. Es sub-issue de la Epic. Su descripción es el contenido del `proposal.md` del change `bootstrap-mvp` (puedes copiarlo tal cual desde `openspec/changes/bootstrap-mvp/proposal.md`). En la sección "Trazabilidad" del body, añade enlaces a: el change (`openspec/changes/bootstrap-mvp/`), el spec (`openspec/specs/auth-local/spec.md`), la PR que acabas de abrir en la oleada 5, y los mockups asociados en `docs/ux/mockups/01-login.html`, `07-splash.html`, `08-registro.html`. Estado: `Done`.

Dentro de la Feature, crea **una Story por cada requirement** del spec (`auth-local/spec.md`). Si el spec tiene cinco requirements R-1…R-5, tendrás cinco Stories sub-issue de la Feature. El título sigue el patrón `ST-AUTH-LOCAL-R{N} · {título descriptivo del requirement}`. Por ejemplo: `ST-AUTH-LOCAL-R1 · Registro de usuario con email único`, `ST-AUTH-LOCAL-R2 · Login con credenciales válidas`, `ST-AUTH-LOCAL-R3 · Emisión y validación de JWT`, `ST-AUTH-LOCAL-R4 · Rate limiting de endpoints públicos`, `ST-AUTH-LOCAL-R5 · Auditoría de intentos de login`. En el body de cada Story, copia los scenarios Given/When/Then del requirement correspondiente tal como aparecen en el spec, y añade una sección "Tests que la cubren" con la lista de tests que la oleada 4 verificó para ese requirement. Estado: `Done`.

Dentro de cada Story, crea **una Task por cada T-NNN de `tasks.md` que esté lógicamente asociado a esa Story**. La asociación se establece así: lee el contenido de cada T-NNN y decide a qué requirement contribuye. Si una tarea contribuye a varios (típico de `T-001 crear módulo Maven auth-local`, que es transversal a todos los requirements), créala como Task hija de la Story que más naturalmente la abarque (en ese caso, normalmente R-1 que es la primera funcionalmente significativa) y referencia las otras en el body con "También cubre: ST-AUTH-LOCAL-R{N}". El título de cada Task sigue el patrón `TK-T{NNN} · {título de la tarea en tasks.md}`. En el body de cada Task, incluye: descripción literal de tasks.md, lista de archivos creados o modificados (los conoces porque acabas de ejecutar el change), commit(s) SHA que la implementan (extraíbles del log de la rama `feat/bootstrap-mvp`), y enlace a la PR. Estado: `Done`.

Cada nivel debe tener relación `sub-issue of` correctamente declarada hacia su padre, usando la API de sub-issues de GitHub (no solo mencionando el padre en el body; el campo de jerarquía nativo). Verifica con `gh api graphql` o el comando equivalente que los sub-issues están enlazados, no solo referenciados textualmente.

Asigna cada issue al Project v2 con `gh project item-add 1 --owner lcasadov --url <issue-url>`. Configura los campos del Project para cada item: status `Done`, type según el nivel (Epic/Feature/Story/Task), iteration o sprint si esos campos existen (asigna al sprint corriente o al que `lcasadov` haya configurado como "bootstrap"). Si algún campo no existe en el Project, no lo fuerces: déjalo sin asignar y registra una nota.

Antes de cerrar la oleada, valida:

- Existe exactamente 1 Epic, 1 Feature, N Stories (una por requirement del spec) y M Tasks (una por T-NNN de tasks.md). Cuenta y compáralo con el spec y tasks.md.
- Todos los issues están añadidos al Project v2 y en estado `Done`.
- La relación de sub-issues respeta la jerarquía: Tasks bajo Stories, Stories bajo Feature, Feature bajo Epic.
- Cada Task referencia un T-NNN existente; ningún T-NNN se ha quedado sin Task asociada.
- Cada Story referencia un R-{N} existente; ningún requirement se ha quedado sin Story asociada.
- Los enlaces a archivos del repo (spec, mockups, PR) resuelven correctamente.

Si alguna validación falla, no cierres la oleada: corrige y vuelve a validar. La trazabilidad rota desde el primer change envenena el seguimiento de todos los futuros.

Comenta en el issue del change un resumen de lo creado: número de issues por nivel, URL de la Epic raíz, y un recordatorio para `lcasadov` de que de aquí en adelante los nuevos changes seguirán este mismo patrón de poblar el Project en su oleada 6.

Durante toda la ejecución, si cualquier agente downstream te devuelve `status: blocked` con un motivo concreto, no intentes resolverlo tú reinterpretando el spec o el design. Para la ejecución completa, comenta el bloqueo en el issue, etiqueta a `lcasadov`, y devuelve tú mismo `status: blocked` con la lista de bloqueos. El change es atómico: o entra entero o no entra.

Y una última norma operativa: cada vez que repartas una tarea a un agente, pásale por contexto los artefactos OpenSpec exactos que necesita (no le mandes "lee el change", sino "lee este requirement R-2 y estos tres scenarios"). Los agentes downstream trabajarán mejor con contexto acotado que con el change entero. Tú eres el único que necesita la visión global.