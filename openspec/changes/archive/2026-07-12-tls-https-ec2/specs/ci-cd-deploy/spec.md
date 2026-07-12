## ADDED Requirements

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
