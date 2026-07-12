## 1. Reverse-proxy Caddy

- [x] 1.1 Crear `caddy/Caddyfile` parametrizado por `{$PUBLIC_HOST}`: TLS automático, redirect 80→443, `/api/*` y `/actuator/*` → `backend:8080`, resto → `frontend:5173` (con `handle`, preservando la ruta)
- [x] 1.2 Soporte de validación local: `PUBLIC_HOST=localhost` → Caddy usa CA interna (sin Let's Encrypt real)

## 2. Compose de producción

- [x] 2.1 Crear `docker-compose.prod.yml` (stack autocontenido) con el servicio `caddy` (`caddy:2-alpine`), puertos 80/443, volúmenes `caddy_data`/`caddy_config`, `depends_on` frontend+backend healthy
- [x] 2.2 En el compose de prod, `frontend` (5173) y `backend` (8080) NO publican puertos al host (solo red interna); solo `caddy` expone 80/443
- [x] 2.3 Fijar `VITE_API_BASE_URL=/api` para el frontend (mismo origen; no interviene en código pero se mantiene por compat)

## 3. Frontend mismo origen

- [x] 3.1 Verificado: `httpClient` ya usa `baseURL: '/api'` (relativo) y `VITE_API_BASE_URL` no se referencia en el código → el frontend ya es same-origin; no requiere cambios

## 4. Runbook y documentación

- [x] 4.1 Ampliado `docs/DEPLOYMENT-RUNBOOK.md` §9 (TLS): Elastic IP, puertos 80/443, `PUBLIC_HOST` sslip.io, arranque con `-f docker-compose.prod.yml`, staging de LE, verificación y rollback
- [x] 4.2 Documentada `PUBLIC_HOST` en `.env.example` y en la tabla de variables del runbook; Security Group actualizado (80/443)

## 5. Validación local

- [x] 5.1 Enrutado validado con upstreams `whoami`: `/`→FRONTEND, `/api/reservas`→BACKEND, `/actuator/health`→BACKEND ✅
- [x] 5.2 Sin mixed content: el frontend llama a `/api` relativo (mismo origen); no hay URL absoluta a `:8080`/`http` en el código
- [x] 5.3 `caddy validate` en verde (confirma también el redirect automático 80→443)

## 6. QA y cierre

- [x] 6.1 Auto-revisión de config/enrutado/runbook (no se accede al EC2; validación de emisión ACME queda como paso de runbook)
- [x] 6.2 Sin cambios de código de app → suite backend/frontend inalterada (no hay regresión posible)
- [x] 6.3 Actualizar `openspec/plan.md` (cerrar deuda «TLS/dominio») y crear PR
