# Capability: ci-cd-deploy

## Resumen
Pipeline de integración y despliegue continuos (transversal, no de producto). Un único workflow de GitHub Actions (`.github/workflows/ci.yml`): job `build-and-test` (backend Maven/JDK17 con IT Testcontainers y gate JaCoCo 80%; frontend lint/test/build) y job `deploy` (SSH a la instancia EC2 → `git pull` + `docker compose down` + `docker compose up -d --build`), gated tras los tests y limitado a `push` en `develop`. Modelo replicado del proyecto de referencia `AI4Devs-pipeline-202602-ro`. Runbook operativo en `docs/DEPLOYMENT-RUNBOOK.md`.

## Fase
🟢 Fase 1 (transversal) — implementado y en producción (change `ci-cd-aws-deploy`, PR #164)

## Requirements

### Requirement 1: Build y test automáticos de backend y frontend

**El pipeline DEBE ejecutar, en cada `push` y `pull_request`, el build y los tests del backend (unit + integración con Testcontainers) y del frontend (lint, test, build), y DEBE fallar si cualquiera de ellos falla.**

#### Scenario: Push con todo correcto
- **WHEN** se hace `push` de un commit cuyo backend y frontend compilan y todos los tests pasan
- **THEN** el job `build-and-test` termina en verde (los IT con `@Testcontainers` se ejecutan de verdad — el runner dispone de Docker)

#### Scenario: Test fallido bloquea el pipeline
- **WHEN** un commit introduce un test que falla o rompe la compilación
- **THEN** `build-and-test` termina en rojo y el job de deploy NO se ejecuta

### Requirement 2: Gate de cobertura de código

**El pipeline DEBE hacer fallar el build cuando la cobertura de líneas del backend esté por debajo del umbral del proyecto (`jacoco-check`, 80%).**

#### Scenario: Cobertura por debajo del umbral
- **WHEN** la cobertura de líneas del backend cae por debajo del 80%
- **THEN** `mvn verify` falla en `build-and-test`

### Requirement 3: Despliegue continuo a AWS EC2 tras pasar los tests

**El pipeline DEBE desplegar únicamente cuando `build-and-test` ha pasado y el evento es un `push` a `develop`; el despliegue se hace por SSH ejecutando `git pull` y reconstruyendo con `docker compose`. Flyway aplica las migraciones automáticamente al arrancar el backend.**

#### Scenario: Push a develop con tests en verde
- **WHEN** se hace `push` a `develop` y `build-and-test` termina en verde
- **THEN** el job `deploy` entra por SSH al EC2, hace `git pull` + `docker compose down` + `up -d --build`, y los servicios quedan levantados con healthchecks en verde

#### Scenario: Tests en rojo no despliegan
- **WHEN** `build-and-test` termina en rojo
- **THEN** `deploy` no se ejecuta y el estado del EC2 no cambia

### Requirement 4: Los pull requests no despliegan

**El pipeline DEBE ejecutar solo build y test (nunca deploy) para eventos `pull_request`.**

#### Scenario: PR abierto
- **WHEN** se abre o actualiza un `pull_request`
- **THEN** se ejecuta `build-and-test` y el job `deploy` aparece como `skipping`

### Requirement 5: Aislamiento de secretos

**El pipeline DEBE obtener las credenciales SSH desde GitHub Secrets (`EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`) y NO DEBE contener secretos en el repositorio ni exponerlos en logs; los secretos de la aplicación viven en el `.env` del EC2, fuera del control de versiones.**

#### Scenario: Credenciales SSH desde GitHub Secrets
- **WHEN** el job `deploy` se conecta al EC2
- **THEN** usa los secrets del repo y ningún secreto aparece en el workflow ni en los logs

#### Scenario: Secretos de la aplicación no versionados
- **WHEN** se inspecciona el repositorio
- **THEN** no existe ningún `.env` con secretos commiteado (cubierto por `.gitignore`); la app en el EC2 lee su `.env` local

## Dependencias / referencias
- Runbook: `docs/DEPLOYMENT-RUNBOOK.md` (provisión EC2, secrets, primer deploy, backup `pg_dump`, rollback, evolución a RDS).
- Deuda conocida: TLS/dominio (reverse proxy) y build de imágenes en CI + registry quedan como evolución.
