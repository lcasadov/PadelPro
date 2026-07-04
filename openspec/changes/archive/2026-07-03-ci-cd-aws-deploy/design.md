## Context

PadelPro carece de CI/CD. El stack: backend Spring Boot 3 / JDK 17 / Maven con suite JUnit + Testcontainers y gate JaCoCo (80% líneas); frontend React + Vite (scripts `lint` eslint, `test` vitest, `build` tsc+vite); orquestación con `docker-compose.yml` (postgres + backend:8080 + frontend:5173, con healthchecks) y Dockerfiles multistage ya existentes. `BASE_BRANCH` del repo es `develop`. Identidad de operaciones git/gh: `orquestadoria`.

El proyecto de referencia `AI4Devs-pipeline-202602-ro` resuelve esto con un único `ci.yml`: un job de build+test y un job de deploy que entra por SSH a una EC2 y hace `git pull` + `docker compose down` + `docker compose up -d --build`. Este change replica ese modelo adaptándolo al stack de PadelPro.

## Goals / Non-Goals

**Goals:**
- Ejecutar build + test de backend (incluidos IT con Testcontainers) y frontend en cada push/PR.
- Validar el gate de cobertura JaCoCo en CI.
- Desplegar automáticamente a una EC2 vía `docker compose` por SSH tras pasar los tests, solo en la rama de release.
- Gestionar secretos sin commitearlos.

**Non-Goals:**
- IaC, ECS/EKS, autoescalado, zero-downtime, RDS gestionado, TLS gestionado por la nube (más allá de lo mínimo), observabilidad avanzada.

## Decisions

### D1 — Rama que dispara el deploy: `develop`
Se despliega desde `develop` (rama base y entorno único de demo del proyecto). `pull_request` ejecuta solo build+test; el `push` a `develop` ejecuta build+test y, si pasan, deploy.
- **Alternativa:** `main` con git-flow de promoción → más ceremonia, innecesaria para el entorno único actual.
- **Razón:** el repo trabaja sobre `develop`; un solo entorno desplegado simplifica el MVP.

### D2 — Base de datos en producción: contenedor postgres del compose
Se mantiene el servicio `db` del `docker-compose.yml` (datos en volumen Docker), igual que la referencia.
- **Alternativa:** AWS RDS gestionado → más robusto (backups, HA) pero se sale del "de la misma forma" y añade coste/configuración.
- **Razón:** replicar el modelo de referencia; RDS queda como evolución futura documentada. **Riesgo de pérdida de datos** mitigado abajo.

### D3 — Migraciones Flyway en el deploy: automáticas al arrancar
El backend ejecuta Flyway en el arranque (`ddl-auto=validate`); `docker compose up -d --build` levanta el backend que aplica las migraciones pendientes contra el `db`. No hay paso manual.
- **Razón:** el flujo ya está integrado en la app; el deploy solo reconstruye y reinicia. El `depends_on: db healthy` del compose garantiza el orden.

### D4 — Gestión de secretos: GitHub Secrets (SSH) + `.env` en el EC2 (app)
`EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY` viven como GitHub repo secrets y solo los usa el job de deploy. Los secretos de la app (`JWT_SECRET`, `ENCRYPTION_KEY`, `POSTGRES_PASSWORD`, …) viven en un `.env` creado a mano en el EC2 y leído por `docker compose`; nunca se commitean ni viajan por el workflow.
- **Razón:** mínima superficie de exposición; el workflow solo necesita credenciales SSH, no los secretos de la app.

### D5 — Exposición: IP:puerto directo en v1 (TLS como evolución)
Se expone el frontend (5173) y el backend (8080) directamente vía el security group, como la referencia. TLS/dominio con reverse proxy (Caddy/nginx) se anota como mejora futura.
- **Razón:** paridad con la referencia y simplicidad del MVP; el endurecimiento (TLS, proxy) es un segundo paso.

### D6 — Frontend servido como build estático (ajuste vs dev)
El `frontend/Dockerfile` actual arranca `vite dev` (`--host`). Para producción conviene servir el build estático (p.ej. `vite preview` o nginx sirviendo `dist/`). Se evalúa como ajuste deploy-ready dentro de este change.
- **Razón:** `vite dev` no es apto para producción; pero el cambio debe ser mínimo y no romper el `docker-compose` de desarrollo.

## Risks / Trade-offs

- **Pérdida de datos del postgres en contenedor (D2)** → si el volumen se borra, se pierden reservas/pagos (datos fiscales). **Mitigación:** volumen Docker nombrado persistente (ya en el compose), documentar backup periódico de `pg_dump` en el runbook; marcar RDS como evolución.
- **`docker compose down` provoca downtime** en cada deploy → ventana de indisponibilidad breve. **Mitigación:** aceptable para un entorno de demo; el deploy reconstruye en segundos/minutos; zero-downtime queda fuera de alcance.
- **Secretos mal gestionados** → fuga de credenciales. **Mitigación:** D4 (nunca en repo ni en logs del workflow); `.gitignore` cubre `.env`; los secrets SSH solo en el job de deploy.
- **IT de Testcontainers lentos/flaky en CI** → builds largos. **Mitigación:** caché de Maven y de imágenes; si la suite IT es muy lenta, separar en job aparte (evolución).
- **Migración Flyway fallida en deploy (D3)** → backend no arranca, healthcheck falla. **Mitigación:** el job build-and-test ya ejecuta las migraciones contra Postgres real (Testcontainers), detectando fallos antes del deploy; el `healthcheck` del compose evidencia el fallo.
- **`vite dev` en producción (D6)** → rendimiento/seguridad pobres si no se ajusta. **Mitigación:** servir build estático en el Dockerfile de deploy.

## Migration Plan

1. Provisión manual del EC2 (Docker instalado, repo clonado, `.env` creado, security group con puertos) — documentado en el runbook.
2. Alta de los GitHub Secrets `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY`.
3. Merge del workflow a `develop` → primer build+test; si pasa, primer deploy automático.
4. **Rollback:** el workflow es additivo (un fichero + secrets); revertir = borrar `ci.yml`. Un deploy fallido se revierte en el EC2 con `git checkout <commit-anterior> && docker compose up -d --build`.

## Open Questions

- ¿Una sola EC2 para todo (db+back+front) o separar la BD desde el principio? (v1: todo junto, D2.)
- ¿Región AWS y tipo de instancia? (lo fija el runbook; no afecta al workflow.)
- ¿Se quiere también un job que publique el informe de cobertura como artefacto/PR comment?
