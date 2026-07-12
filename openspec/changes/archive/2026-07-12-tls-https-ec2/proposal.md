## Why

Hoy la aplicación en producción (EC2) se sirve por **HTTP plano** (frontend `:5173`, backend `:8080`). Esto tiene dos consecuencias graves: (1) rompe las APIs del navegador restringidas a *secure context* — fue la causa raíz del bug #201 (`crypto.randomUUID` no existe fuera de HTTPS/localhost, impidiendo crear reservas en prod); y (2) expone credenciales, JWT y datos personales en claro por la red, incumpliendo cualquier estándar mínimo de producción y el diseño de seguridad del proyecto. Necesitamos terminación TLS ya, sin depender de comprar un dominio.

## What Changes

- Se añade un servicio **Caddy** como reverse-proxy delante del stack, que termina TLS en `:443` y redirige `:80 → :443`.
- Caddy obtiene y renueva **certificados Let's Encrypt automáticamente** usando un dominio **`sslip.io`** derivado de la IP fija (Elastic IP) del EC2 — sin comprar dominio ni gestionar DNS.
- Caddy hace **proxy inverso**: `/` → `frontend:5173`, y `/api/*` + `/actuator/*` → `backend:8080`. El frontend deja de exponer su puerto directamente.
- Se entrega un **`docker-compose.prod.yml`** (overlay del `docker-compose.yml` base) con el servicio `caddy`, el `Caddyfile`, un volumen persistente para los certificados, y variables parametrizadas (`PUBLIC_HOST`).
- Se entrega un **runbook de despliegue** (`docs/DEPLOYMENT-RUNBOOK.md` ampliado) con los pasos exactos para aplicarlo en el EC2: Elastic IP, apertura de puertos 80/443, cálculo del host `sslip.io`, arranque y verificación.
- **BREAKING (operativo, no de API):** el punto de entrada público pasa de `http://<ip>:5173` a `https://<ip-guionizada>.sslip.io`. El frontend debe llamar al backend por ruta relativa `/api` (mismo origen), evitando *mixed content*.

## Capabilities

### New Capabilities
<!-- Ninguna capability de producto nueva; es infraestructura transversal. -->

### Modified Capabilities
- `ci-cd-deploy`: se añade el requisito de **terminación TLS/HTTPS en producción** mediante reverse-proxy con certificado automático (Let's Encrypt vía `sslip.io`), redirección HTTP→HTTPS y enrutado de `/api` al backend en el mismo origen.

## Impact

- **Infra / despliegue:** nuevo servicio `caddy`, `docker-compose.prod.yml`, `Caddyfile`, volumen de certificados. El servicio `frontend` deja de publicar `5173` al host (solo red interna); `backend` deja de publicar `8080` al host (solo red interna). Solo `caddy` publica `80`/`443`.
- **Frontend:** `VITE_API_BASE_URL` pasa a ruta relativa (`/api`) en producción para servir todo desde el mismo origen HTTPS y evitar *mixed content*.
- **Seguridad (`docs/security-design.md`):** se cierra el gap de transporte en claro; JWT y credenciales viajan cifrados.
- **Dependencias:** imagen `caddy:2-alpine`. Sin cambios de código de negocio.
- **Runbook (`docs/DEPLOYMENT-RUNBOOK.md`):** nueva sección de despliegue con TLS.
- **Fase del producto:** fase-1 (endurecimiento de producción, transversal).

## Fuera de alcance

- **No se accede al EC2** ni se despliega en vivo: este change entrega configuración + runbook validados en local; el equipo los aplica en producción.
- No se compra dominio propio ni se configura DNS gestionado (se usa `sslip.io`). Migrar a un dominio real es un follow-up trivial (cambiar `PUBLIC_HOST`).
- No se añade WAF, rate-limiting a nivel de proxy, ni HSTS preload (se puede añadir después).
- No se cambia la lógica de negocio, autenticación ni el esquema de datos.
