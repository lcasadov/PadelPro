## 0. Gestión y arranque (orquestador)

- [x] 0.1 Crear/identificar Issue en GitHub y obtener su número (#163)
- [x] 0.2 Crear rama `feat/163-ci-cd-aws-deploy` desde `develop` y push
- [x] 0.3 Issue #163 abierto (gestión por Issues)

## 1. Workflow de CI — build y test

- [x] 1.1 Crear `.github/workflows/ci.yml` con triggers `push` a `develop`, `pull_request` y `workflow_dispatch`
- [x] 1.2 Job `build-and-test` (ubuntu-latest): checkout
- [x] 1.3 Backend: `actions/setup-java@v4` JDK 17 (Temurin) + caché Maven; `mvn -f backend/pom.xml verify` (unit + IT con Testcontainers + gate JaCoCo)
- [x] 1.4 Frontend: `actions/setup-node@v4` Node 20 + caché npm; `npm ci` + `npm run lint` + `npm run test` + `npm run build` en `./frontend`
- [x] 1.5 (Opcional) Publicar informe de cobertura JaCoCo como artefacto del workflow

## 2. Workflow de CD — deploy a EC2

- [x] 2.1 Job `deploy`: `needs: build-and-test`, `if` rama de release (`develop`) y evento `push`
- [x] 2.2 Usar `appleboy/ssh-action` con secrets `EC2_HOST` / `EC2_USER` / `EC2_SSH_KEY`
- [x] 2.3 Script remoto: `cd <repo>` → `git pull origin develop` → `docker compose down` → `docker compose up -d --build`
- [x] 2.4 Verificar que `pull_request` NO dispara `deploy` (solo `build-and-test`)

## 3. Infra deploy-ready (docker)

- [x] 3.1 Revisar `docker-compose.yml`: inyección de `.env`, `restart: unless-stopped`, healthchecks, volumen persistente del postgres
- [x] 3.2 Ajustar `frontend/Dockerfile` para servir el build estático en producción (en vez de `vite dev`), sin romper el compose de desarrollo
- [x] 3.3 Confirmar que el backend aplica Flyway al arrancar con `SPRING_PROFILES_ACTIVE=prod` y que `depends_on: db healthy` ordena el arranque
- [x] 3.4 Verificar que `.gitignore` cubre `.env` y que no hay secretos commiteados

## 4. Provisión AWS (manual, documentada)

> Pendiente — manual. Sin credenciales AWS; documentado paso a paso en `docs/DEPLOYMENT-RUNBOOK.md` §2.

- [x] 4.1 EC2 reutilizada (16.192.61.61, compartida con LTI); security group abierto para 8080/5173 (2026-06-27)
- [x] 4.2 Docker + compose ya presentes en la instancia
- [x] 4.3 Repo clonado en ~/PadelPro y `.env` creado con secretos generados en la máquina
- [x] 4.4 Secrets `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY` configurados vía gh secret set

## 5. Documentación

- [x] 5.1 Runbook de despliegue en `docs/` (provisión EC2, Docker, clonado, `.env`, secrets, primer deploy)
- [x] 5.2 Documentar backup del postgres (`pg_dump`) y nota de evolución a RDS
- [x] 5.3 Documentar rollback (checkout commit anterior + `docker compose up -d --build`)

## 6. Verificación

> Validación REAL en GitHub Actions sobre la PR #164 (runs 27877583908 y 28017208805).
> El primer run destapó 6 IT preexistentes rotos (auth/usuarios) + 1 bug real de producción
> (`RateLimitFilter` usaba `getServletPath()`); corregidos. Segundo run: build+test VERDE (2m27s).

- [x] 6.1 PR #164 → corre `build-and-test` y el job `deploy` aparece como `skipping` (no se ejecuta en PR)
- [x] 6.2 Tests rojos (primer run) → pipeline en rojo y `deploy` saltado. Confirmado.
- [x] 6.3 Merge a `develop` → deploy automático verificado (múltiples deploys SUCCESS desde 2026-06-27)
- [x] 6.4 Verificado en vivo: contenedores healthy, Flyway aplicado, /actuator/health=200, frontend accesible en :5173

## 7. Cierre

- [x] 7.1 PR #164 con `Closes #163`; CI (`build-and-test`) en VERDE sobre sí mismo
- [x] 7.2 Primer despliegue real verificado (tras fixes #165/#167/#169/#171 de prod-readiness)
