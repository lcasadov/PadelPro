# Capability: ci-cd-deploy

## Purpose
Pipeline de integración y despliegue continuos (transversal, no de producto). Un único workflow de GitHub Actions (`.github/workflows/ci.yml`): job `build-and-test` (backend Maven/JDK17 con IT Testcontainers y gate JaCoCo 80%; frontend lint/test/build) y job `deploy` (SSH a la instancia EC2 → `git pull` + `docker compose down` + `docker compose up -d --build`), gated tras los tests y limitado a `push` en `develop`. Modelo replicado del proyecto de referencia `AI4Devs-pipeline-202602-ro`. Runbook operativo en `docs/DEPLOYMENT-RUNBOOK.md`.

## Fase
🟢 Fase 1 (transversal) — implementado y en producción (change `ci-cd-aws-deploy`, PR #164)
## Requirements

### Requirement 1: Build y test automáticos de backend y frontend

**El pipeline DEBE (MUST) ejecutar, en cada `push` y `pull_request`, el build y los tests del backend (unit + integración con Testcontainers) y del frontend (lint, test, build), y DEBE fallar si cualquiera de ellos falla.**

#### Scenario: Push con todo correcto
- **WHEN** se hace `push` de un commit cuyo backend y frontend compilan y todos los tests pasan
- **THEN** el job `build-and-test` termina en verde (los IT con `@Testcontainers` se ejecutan de verdad — el runner dispone de Docker)

#### Scenario: Test fallido bloquea el pipeline
- **WHEN** un commit introduce un test que falla o rompe la compilación
- **THEN** `build-and-test` termina en rojo y el job de deploy NO se ejecuta

### Requirement 2: Gate de cobertura de código

**El pipeline DEBE (MUST) hacer fallar el build cuando la cobertura de líneas del backend esté por debajo del umbral del proyecto (`jacoco-check`, 80%).**

#### Scenario: Cobertura por debajo del umbral
- **WHEN** la cobertura de líneas del backend cae por debajo del 80%
- **THEN** `mvn verify` falla en `build-and-test`

### Requirement 3: Despliegue continuo a AWS EC2 tras pasar los tests

**El pipeline DEBE (MUST) desplegar únicamente cuando `build-and-test` ha pasado y el evento es un `push` a `develop`; el despliegue se hace por SSH ejecutando `git pull` y reconstruyendo con `docker compose`. Flyway aplica las migraciones automáticamente al arrancar el backend.**

#### Scenario: Push a develop con tests en verde
- **WHEN** se hace `push` a `develop` y `build-and-test` termina en verde
- **THEN** el job `deploy` entra por SSH al EC2, hace `git pull` + `docker compose down` + `up -d --build`, y los servicios quedan levantados con healthchecks en verde

#### Scenario: Tests en rojo no despliegan
- **WHEN** `build-and-test` termina en rojo
- **THEN** `deploy` no se ejecuta y el estado del EC2 no cambia

### Requirement 4: Los pull requests no despliegan

**El pipeline DEBE (MUST) ejecutar solo build y test (nunca deploy) para eventos `pull_request`.**

#### Scenario: PR abierto
- **WHEN** se abre o actualiza un `pull_request`
- **THEN** se ejecuta `build-and-test` y el job `deploy` aparece como `skipping`

### Requirement 5: Aislamiento de secretos

**El pipeline DEBE (MUST) obtener las credenciales SSH desde GitHub Secrets (`EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`) y NO DEBE contener secretos en el repositorio ni exponerlos en logs; los secretos de la aplicación viven en el `.env` del EC2, fuera del control de versiones.**

#### Scenario: Credenciales SSH desde GitHub Secrets
- **WHEN** el job `deploy` se conecta al EC2
- **THEN** usa los secrets del repo y ningún secreto aparece en el workflow ni en los logs

#### Scenario: Secretos de la aplicación no versionados
- **WHEN** se inspecciona el repositorio
- **THEN** no existe ningún `.env` con secretos commiteado (cubierto por `.gitignore`); la app en el EC2 lee su `.env` local

### Requirement: Suite E2E smoke del journey crítico en el pipeline

El pipeline DEBE (MUST) ejecutar una suite de tests **end-to-end** con un navegador real (Playwright/Chromium) que ejercite el journey crítico **iniciar sesión → crear reserva** contra el stack completo levantado con `docker compose` (frontend build + backend + base de datos reales, **sin mocks**). El job E2E DEBE (MUST) correr después de `build-and-test`, tanto en `pull_request` como en `push`, y su fallo DEBE (MUST) bloquear el despliegue.

#### Scenario: Journey crítico en verde
- **WHEN** se abre un `pull_request` o se hace `push` y el stack levanta correctamente
- **THEN** el job `e2e` inicia sesión, crea una reserva a través de la UI real y verifica la pantalla de confirmación; termina en verde

#### Scenario: Fallo del journey bloquea el deploy
- **WHEN** el smoke E2E falla (p. ej. el flujo de crear reserva se rompe, como en el estilo del bug #201)
- **THEN** el pipeline queda en rojo y el job `deploy` NO se ejecuta

#### Scenario: Evidencia en caso de fallo
- **WHEN** un test E2E falla en CI
- **THEN** se publican como artefactos la traza de Playwright y/o capturas para diagnóstico

### Requirement: Ejecución del E2E contra el stack real sin mocks

La suite E2E DEBE (MUST) apuntar al frontend servido como build de producción y al backend real (no debe usar MSW ni dobles de red), de modo que reproduzca el comportamiento observable por un usuario y detecte regresiones de integración que los tests de componente con mocks no pueden ver.

#### Scenario: Sin dobles de red
- **WHEN** se ejecuta la suite E2E
- **THEN** las peticiones del navegador llegan al backend real y a la base de datos, sin interceptores ni respuestas simuladas

### Requirement: Terminación TLS/HTTPS en producción

El despliegue de producción DEBE (MUST) servir la aplicación exclusivamente por **HTTPS**, terminando TLS en un **reverse-proxy** (Caddy) que escucha en el puerto `443`. El proxy MUST redirigir todo el tráfico HTTP del puerto `80` a HTTPS. Los servicios `frontend` y `backend` NO DEBEN publicar sus puertos (`5173`, `8080`) directamente al host: solo el reverse-proxy expone `80`/`443`.

#### Scenario: Acceso por HTTP redirige a HTTPS
- **WHEN** un cliente solicita `http://<host>/` en el puerto 80
- **THEN** el proxy responde con una redirección (3xx) a `https://<host>/`

#### Scenario: El frontend y el backend no son accesibles directamente
- **WHEN** se inspeccionan los puertos publicados por el stack de producción
- **THEN** únicamente el reverse-proxy publica `80` y `443` al host; `frontend` (`5173`) y `backend` (`8080`) solo son alcanzables por la red interna de Docker

#### Scenario: El contexto seguro del navegador queda disponible
- **WHEN** la aplicación se carga sobre HTTPS
- **THEN** las APIs restringidas a *secure context* (p. ej. `crypto.randomUUID`) están disponibles y el flujo de crear reserva no falla por ese motivo (cierra la causa del bug #201)

### Requirement: Certificado automático sin dominio propio (Let's Encrypt vía sslip.io)

El reverse-proxy DEBE (MUST) obtener y renovar automáticamente un certificado TLS de una CA pública (Let's Encrypt) usando un nombre de host derivado de la IP fija de la instancia mediante **`sslip.io`** (`<ip-con-guiones>.sslip.io`), sin necesidad de comprar dominio ni gestionar un servidor DNS. El host público MUST ser configurable mediante la variable `PUBLIC_HOST` para permitir migrar a un dominio real en el futuro sin cambios de código.

#### Scenario: Emisión automática del certificado
- **WHEN** el reverse-proxy arranca con `PUBLIC_HOST=<ip-con-guiones>.sslip.io` y los puertos 80/443 accesibles desde Internet
- **THEN** obtiene un certificado válido de Let's Encrypt para ese host y sirve HTTPS sin intervención manual

#### Scenario: Persistencia de certificados entre reinicios
- **WHEN** el stack se reinicia (`docker compose down` + `up`)
- **THEN** los certificados y datos ACME persisten en un volumen dedicado y no se vuelve a solicitar un certificado nuevo (evita el rate-limit de Let's Encrypt)

#### Scenario: Migración a dominio propio
- **WHEN** se cambia `PUBLIC_HOST` a un dominio real que apunta a la IP
- **THEN** el proxy emite el certificado para ese dominio sin cambios en la configuración más allá de la variable

### Requirement: Enrutado de API en el mismo origen

El reverse-proxy DEBE (MUST) enrutar las peticiones a `/api/*` y `/actuator/*` hacia el `backend`, y el resto (`/`) hacia el `frontend`, de modo que el navegador use un **único origen HTTPS** y no se produzca *mixed content*. El frontend en producción MUST llamar al backend por ruta relativa (`/api`), no por una URL absoluta a otro puerto.

#### Scenario: Petición de API servida desde el mismo origen
- **WHEN** el frontend cargado en `https://<host>/` hace una petición a `/api/reservas`
- **THEN** el proxy la enruta al backend y la respuesta llega por HTTPS desde el mismo origen, sin advertencias de *mixed content*

#### Scenario: Recurso de frontend servido por el proxy
- **WHEN** se solicita `https://<host>/` o cualquier ruta del SPA
- **THEN** el proxy devuelve el contenido del `frontend`

## Dependencias / referencias
- Runbook: `docs/DEPLOYMENT-RUNBOOK.md` (provisión EC2, secrets, primer deploy, backup `pg_dump`, rollback, evolución a RDS).
- TLS/HTTPS en producción: reverse-proxy Caddy + Let's Encrypt (sslip.io) — ver §9 del runbook y `docker-compose.prod.yml` (change `tls-https-ec2`).
- Deuda conocida: build de imágenes en CI + registry queda como evolución.
