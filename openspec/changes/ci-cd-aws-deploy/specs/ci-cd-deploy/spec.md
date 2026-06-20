## ADDED Requirements

### Requirement: Build y test automáticos de backend y frontend

El pipeline SHALL ejecutar, en cada `push` y `pull_request`, el build y los tests del backend (unit + integración con Testcontainers) y del frontend (lint, test, build), y SHALL fallar si cualquiera de ellos falla.

#### Scenario: Push con backend y frontend correctos
- **WHEN** se hace `push` de un commit cuyo backend y frontend compilan y todos los tests pasan
- **THEN** el job `build-and-test` termina en verde
- **AND** los IT del backend con `@Testcontainers` se ejecutan realmente (el runner dispone de Docker)

#### Scenario: Test de backend fallido bloquea el pipeline
- **WHEN** un commit introduce un test de backend que falla
- **THEN** el job `build-and-test` termina en rojo
- **AND** el job de deploy NO se ejecuta

#### Scenario: Build de frontend fallido bloquea el pipeline
- **WHEN** un commit rompe la compilación del frontend (`npm run build`)
- **THEN** el job `build-and-test` termina en rojo

### Requirement: Gate de cobertura de código

El pipeline SHALL hacer fallar el build cuando la cobertura de líneas del backend esté por debajo del umbral del proyecto (`jacoco-check`, 80%).

#### Scenario: Cobertura por debajo del umbral
- **WHEN** la cobertura de líneas del backend cae por debajo del 80%
- **THEN** `mvn verify` falla en el job `build-and-test` por el gate de JaCoCo

### Requirement: Despliegue continuo a AWS EC2 tras pasar los tests

El pipeline SHALL desplegar la aplicación a la instancia EC2 únicamente cuando el job de build y test ha pasado y el evento es un `push` a la rama de release; el despliegue SHALL hacerse por SSH ejecutando `git pull` y reconstruyendo los servicios con `docker compose`.

#### Scenario: Push a la rama de release con tests en verde
- **WHEN** se hace `push` a la rama de release y `build-and-test` termina en verde
- **THEN** el job `deploy` entra por SSH al EC2 y ejecuta `git pull` + `docker compose down` + `docker compose up -d --build`
- **AND** los servicios (db, backend, frontend) quedan levantados con sus healthchecks en verde

#### Scenario: Tests en rojo no despliegan
- **WHEN** `build-and-test` termina en rojo
- **THEN** el job `deploy` no se ejecuta y el estado del EC2 no cambia

#### Scenario: Migraciones Flyway aplicadas en el arranque
- **WHEN** el deploy levanta el backend con `docker compose up`
- **THEN** Flyway aplica automáticamente las migraciones pendientes contra el postgres antes de aceptar tráfico, sin pasos manuales

### Requirement: Los pull requests no despliegan

El pipeline SHALL ejecutar solo build y test (nunca deploy) para eventos `pull_request`.

#### Scenario: PR abierto contra la rama de release
- **WHEN** se abre o actualiza un `pull_request`
- **THEN** se ejecuta `build-and-test`
- **AND** el job `deploy` no se ejecuta

### Requirement: Aislamiento de secretos

El pipeline SHALL obtener las credenciales SSH desde GitHub Secrets y SHALL NOT contener secretos en el repositorio ni exponerlos en los logs; los secretos de la aplicación SHALL residir en un `.env` presente en el EC2, fuera del control de versiones.

#### Scenario: Credenciales SSH desde GitHub Secrets
- **WHEN** el job `deploy` se conecta al EC2
- **THEN** usa `EC2_HOST`, `EC2_USER` y `EC2_SSH_KEY` desde GitHub Secrets
- **AND** ningún secreto aparece en el fichero del workflow ni en los logs de ejecución

#### Scenario: Secretos de la aplicación no versionados
- **WHEN** se inspecciona el repositorio
- **THEN** no existe ningún `.env` con secretos commiteado (está cubierto por `.gitignore`)
- **AND** la app en el EC2 lee sus secretos del `.env` local de la instancia
