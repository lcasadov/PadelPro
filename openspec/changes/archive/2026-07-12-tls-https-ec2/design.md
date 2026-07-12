## Context

En producción (EC2) el stack se levanta con `docker-compose.yml`: `db` (5432), `backend` (8080) y `frontend` (5173, `vite preview` sirviendo el build estático). Los tres publican su puerto al host y el acceso público es `http://<ip>:5173`. No hay terminación TLS. Esto rompió `crypto.randomUUID` en el navegador (bug #201) y deja JWT/credenciales en claro. La propia spec de `ci-cd-deploy` ya declaraba «TLS/dominio (reverse proxy)» como deuda conocida.

Restricción operativa clave: **no se compra dominio** y **no se accede al EC2** desde este trabajo — se entrega configuración + runbook validados en local y el equipo los aplica. La IP del EC2 debe ser fija (Elastic IP) para que el certificado siga siendo válido tras reinicios.

## Goals / Non-Goals

**Goals:**
- Servir la app por HTTPS con certificado de confianza, sin comprar dominio.
- Cerrar el gap de *secure context* (motivación directa: #201) y el transporte en claro.
- Entrega reproducible: `docker-compose.prod.yml` + `Caddyfile` + runbook, validable en local.
- Mismo origen HTTPS para frontend y `/api` (sin *mixed content*).

**Non-Goals:**
- Desplegar en vivo en el EC2 (lo hace el equipo con el runbook).
- Dominio propio / DNS gestionado, WAF, HSTS preload, rate-limiting en el proxy.
- Cambios de lógica de negocio, auth o esquema de datos.

## Decisions

### D1 — Caddy como reverse-proxy (frente a Nginx + Certbot)
Se elige **Caddy 2** porque emite y **renueva certificados Let's Encrypt automáticamente** con configuración mínima (una línea con el host), gestiona el reto ACME HTTP-01 en el `:80` que ya vamos a exponer, y renueva sin cron ni scripts. Nginx+Certbot exigiría un contenedor extra, montaje de webroot y renovación programada — más piezas que fallan. Rationale operativo: minimizar superficie de error en un despliegue que el equipo aplicará a mano (menos pasos = menos fallos en prod).

### D2 — `sslip.io` para obtener un host sin comprar dominio
`sslip.io` resuelve `<ip-con-guiones>.sslip.io` (p. ej. `16-192-61-61.sslip.io`) a esa IP, sin registrar nada. Permite a Let's Encrypt validar por HTTP-01 y emitir un certificado **de confianza** (a diferencia de un self-signed, que el navegador rechaza y que NO habilita *secure context* sin excepción manual). Alternativa self-signed descartada: rompe la experiencia y no resuelve limpiamente #201 para usuarios reales. Se parametriza `PUBLIC_HOST` para migrar a dominio propio sin tocar el `Caddyfile`.

### D3 — Overlay `docker-compose.prod.yml` (frente a editar el base)
El `docker-compose.yml` base sigue sirviendo el desarrollo local (puertos directos, sin TLS). Producción usa `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`, que **añade** el servicio `caddy` y **retira la publicación** de puertos de `frontend`/`backend` (quedan solo en la red interna). Así no se degrada el DX local y el cambio de prod es aditivo y reversible (rollback = no usar el overlay).

### D4 — API en el mismo origen por ruta relativa
El frontend en producción llamará a `/api` (relativo) en vez de `http://backend:8080`. Caddy enruta `/api/*` y `/actuator/*` al backend y el resto al frontend. Esto evita *mixed content* (un origen HTTPS no puede llamar a un `http://` ni a otro puerto sin CORS/TLS aparte) y simplifica la config. Se ajusta `VITE_API_BASE_URL` a `/api` en el overlay de prod.

### D5 — Validación en local sin Let's Encrypt real
En local no se puede validar HTTP-01 (no hay IP pública). Se valida el **enrutado** del proxy (que `/` va al frontend y `/api` al backend) usando Caddy con TLS interno/local (`tls internal` o el auto-HTTPS de Caddy para `localhost`). La emisión real de Let's Encrypt se cubre en el runbook y se verifica en el despliegue. La spec distingue ambos: el enrutado es testeable en local; la emisión ACME es un paso de runbook.

## Risks / Trade-offs

- **[La IP del EC2 cambia y el certificado/host deja de valer]** → Mitigación: el runbook exige **Elastic IP** (fija) antes de configurar `PUBLIC_HOST`; se documenta explícitamente como requisito previo.
- **[Rate-limit de Let's Encrypt por reintentos]** → Mitigación: volumen persistente para `/data` de Caddy (certs + ACME) montado en el compose; el runbook advierte de usar el entorno *staging* de LE para pruebas iniciales.
- **[Puertos 80/443 no abiertos en el Security Group]** → Mitigación: el runbook incluye el paso de abrir 80 y 443 en el Security Group del EC2; sin el 80, el reto HTTP-01 falla.
- **[Mixed content si el frontend conserva una URL absoluta http]** → Mitigación: D4 fuerza ruta relativa `/api` en prod; el runbook lo verifica con DevTools (sin peticiones a `:8080`/`http`).
- **[`sslip.io` como dependencia de terceros para DNS]** → Mitigación: solo interviene en la emisión/renovación; una caída puntual no tumba el servicio ya emitido. Migrable a dominio propio con `PUBLIC_HOST` (D2).

## Migration Plan

1. Reservar/asociar **Elastic IP** al EC2 (si no la tiene) y abrir puertos 80/443 en el Security Group.
2. Definir `PUBLIC_HOST=<ip-con-guiones>.sslip.io` en el `.env` del EC2.
3. `git pull` y levantar con el overlay: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`.
4. Verificar emisión del certificado (logs de `caddy`) y acceso `https://<host>/` + `https://<host>/api/...`.
5. **Rollback:** volver a `docker compose up -d` sin el overlay (puertos directos, HTTP) — estado anterior intacto; los certificados quedan en el volumen para el siguiente intento.
