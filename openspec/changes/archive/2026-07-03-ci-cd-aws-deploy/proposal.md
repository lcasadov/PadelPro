## Why

PadelPro no tiene integración continua: los tests no se ejecutan automáticamente en ningún sitio (solo actúa el revisor CodeRabbit). Como consecuencia, 8 IT de auth/usuarios que usan `@Testcontainers` nunca se validan (el host de desarrollo no tiene Docker accesible), y cada merge depende de ejecuciones locales manuales. Tampoco existe despliegue automatizado. Se quiere replicar el modelo probado del proyecto de referencia `AI4Devs-pipeline-202602-ro`: un único workflow de GitHub Actions que construye, testea y despliega a una instancia AWS EC2 vía `docker compose` por SSH.

Fase del producto: **fase-1** (infraestructura transversal).

## What Changes

- **Workflow `.github/workflows/ci.yml`** (nuevo):
  - **Triggers**: `push` a la rama de release, `pull_request` (solo build+test, sin deploy) y `workflow_dispatch`.
  - **Job `build-and-test`** (ubuntu-latest):
    - Backend: `setup-java` JDK 17 (Temurin) + caché Maven; `mvn -f backend/pom.xml verify` (unit + IT). El runner de GitHub **tiene Docker**, así que los IT con `@Testcontainers` (los 8 de auth/usuarios y los de reservas) **sí se ejecutan** — cerrando el hueco actual. Respeta el gate JaCoCo (`jacoco-check`, 80% líneas).
    - Frontend: `setup-node` 20 + `npm ci` + `npm run lint` + `npm run test` (vitest) + `npm run build` en `./frontend`.
  - **Job `deploy`**: `needs: build-and-test`, condicionado a la rama de release; `appleboy/ssh-action` con secrets `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY`; script: `cd <repo>` → `git pull` → `docker compose down` → `docker compose up -d --build`.
- **Reutilización de la infra existente**: `docker-compose.yml` + Dockerfiles (db postgres + backend Spring Boot 8080 + frontend Vite 5173, con healthchecks). Verificar que son deploy-ready y que las variables de producción (`JWT_SECRET`, `ENCRYPTION_KEY`, `POSTGRES_PASSWORD`, `SPRING_PROFILES_ACTIVE=prod`, `VITE_API_BASE_URL`) se inyectan desde un `.env` presente en el EC2 (nunca commiteado).
- **Runbook de despliegue** en `docs/`: provisión del EC2, instalación de Docker, clonado del repo, creación del `.env`, y lista de GitHub Secrets.

## Capabilities

### New Capabilities
- `ci-cd-deploy`: pipeline de integración continua (build + test de backend y frontend con cobertura) y despliegue continuo a AWS EC2 vía `docker compose` por SSH, gated tras los tests y limitado a la rama de release.

### Modified Capabilities
_(ninguna — no cambia comportamiento de capabilities de producto existentes)_

## Impact

- **Nuevo**: `.github/workflows/ci.yml`.
- **Ajustes posibles**: `docker-compose.yml` / Dockerfiles para ser deploy-ready (p.ej. servir el frontend en modo build en vez de `vite dev`, inyección de `.env`).
- **GitHub repo secrets** (nuevos): `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` (y, si se decide inyectar por workflow, los de la app).
- **Infraestructura AWS** (provisión manual, documentada): 1 instancia EC2 con Docker, security group con los puertos necesarios, par de claves SSH.
- **docs/**: runbook de despliegue + lista de secrets.
- **Sin cambios** en el código de aplicación (backend/frontend).

## Fuera de alcance

- IaC (Terraform/CloudFormation): la referencia provisiona el EC2 a mano; este change replica ese modelo.
- AWS ECS/EKS, autoescalado, balanceador de carga, despliegue blue-green / zero-downtime.
- Migración a base de datos gestionada (AWS RDS) — anotada como evolución futura (decisión D2).
- Configuración productiva de Redsys y Telegram (pertenecen a otras capabilities).
- Observabilidad/alertas avanzadas (existe `AI4Devs-monitoring-202602-ro` aparte).
