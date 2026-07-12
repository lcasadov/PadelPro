# Runbook de despliegue — PadelPro (CI/CD a AWS EC2)

Capability: `ci-cd-deploy` (Issue #163). Replica el modelo del proyecto de referencia
`AI4Devs-pipeline-202602-ro`: un único workflow de GitHub Actions
(`.github/workflows/ci.yml`) que construye + testea y, en `push` a `develop`,
despliega por SSH a una instancia EC2 con `docker compose`.

> Decisiones de diseño en `openspec/changes/ci-cd-aws-deploy/design.md` (D1–D6).

---

## 1. Arquitectura del pipeline

```
push a develop ─┐
pull_request ───┼─► build-and-test (ubuntu-latest)
workflow_dispatch┘        │  backend: mvn verify (unit + IT Testcontainers + gate JaCoCo 80%)
                          │  frontend: npm ci → lint → test → build
                          ▼
                  ¿push a develop?  ── sí ──► deploy (SSH a EC2)
                          │                      git pull + docker compose down + up -d --build
                          └── pull_request ──► (NO despliega)
```

- **`build-and-test`** corre siempre (push, PR, dispatch). El runner de GitHub tiene
  Docker, así que los IT con `@Testcontainers` se ejecutan de verdad.
- **`deploy`** solo corre con `if: github.ref == 'refs/heads/develop' && github.event_name == 'push'`.
  Los pull requests **nunca** despliegan.
- **BD** = contenedor postgres del compose con volumen persistente (D2). No RDS.
- **Flyway** se aplica al arrancar el backend (`ddl-auto=validate`, `depends_on: db healthy`) (D3).
- **Frontend en producción** = build estático servido por `vite preview` (D6), no `vite dev`.
- **Exposición** = IP:puerto directa, sin TLS en v1 (D5).

---

## 2. Provisión AWS (MANUAL — pendiente para el usuario)

> Estos pasos **no** los ejecuta el pipeline ni este change (no hay credenciales AWS
> disponibles). Hazlos una sola vez.

### 2.1 Crear el par de claves SSH

```bash
# En tu máquina local
ssh-keygen -t ed25519 -f ~/.ssh/padelpro_ec2 -C "padelpro-deploy"
# Genera ~/.ssh/padelpro_ec2 (privada) y ~/.ssh/padelpro_ec2.pub (pública)
```

En la consola de AWS EC2 → **Key Pairs** → importa la clave **pública**
(`padelpro_ec2.pub`), o crea el par y descarga el `.pem`.

### 2.2 Lanzar la instancia EC2

- AMI: **Ubuntu Server 22.04 LTS** (o Amazon Linux 2023).
- Tipo: **t3.small** mínimo recomendado (backend Spring Boot + postgres + build de imágenes
  necesitan RAM; `t2.micro` puede quedarse corto al compilar). Ajustar según presupuesto.
- Almacenamiento: 30 GB gp3 (imágenes Docker + volumen postgres).
- Asocia el par de claves del paso 2.1.

### 2.3 Security Group

| Tipo       | Puerto | Origen            | Motivo                          |
|------------|--------|-------------------|---------------------------------|
| SSH        | 22     | tu IP / IP runner | acceso administración + deploy  |
| Custom TCP | 8080   | 0.0.0.0/0         | backend (solo despliegue HTTP legacy — cerrar al activar TLS) |
| Custom TCP | 5173   | 0.0.0.0/0         | frontend (solo despliegue HTTP legacy — cerrar al activar TLS) |
| HTTP       | 80     | 0.0.0.0/0         | reto ACME (Let's Encrypt) + redirect a HTTPS (modo TLS) |
| HTTPS      | 443    | 0.0.0.0/0         | acceso público a la app con TLS (modo TLS) |

> ⚠️ Restringir SSH (22) a tu IP. GitHub Actions usa rangos de IP dinámicos; si quieres
> restringir el deploy por IP, considera un self-hosted runner o un bastion. En v1 se
> acepta SSH abierto a tu IP de administración (el deploy usa la clave privada en Secrets).
> 🔒 **Con TLS activado (sección 9)**, cierra 8080/5173 al mundo: el único acceso público
> es 80/443 vía el reverse-proxy Caddy. 8080/5173 solo hacían falta en el despliegue HTTP legacy.

### 2.4 Instalar Docker + Docker Compose en el EC2

```bash
ssh -i ~/.ssh/padelpro_ec2 ubuntu@<EC2_PUBLIC_IP>

# Docker Engine + plugin compose (Ubuntu)
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Permitir docker sin sudo al usuario de deploy
sudo usermod -aG docker $USER
newgrp docker   # o reconecta la sesión SSH

docker --version && docker compose version
```

### 2.5 Clonar el repositorio en el EC2

El script de deploy hace `cd ~/PadelPro && git pull origin develop`. La ruta debe ser
`~/PadelPro` (home del usuario de deploy). Si usas otra ruta, ajústala en el `script:`
del job `deploy` en `.github/workflows/ci.yml`.

```bash
cd ~
git clone https://github.com/<ORG>/<REPO>.git PadelPro
cd PadelPro
git checkout develop
```

> Para repos privados, configura un deploy key o un PAT de solo lectura en el remoto del
> EC2 (`git remote set-url origin git@github.com:...` + deploy key), de modo que
> `git pull origin develop` funcione sin credenciales interactivas.

### 2.6 Crear el `.env` en el EC2 (NUNCA se commitea — D4)

```bash
cd ~/PadelPro
cp .env.example .env
nano .env
```

Rellena con valores **fuertes y aleatorios** de producción:

```dotenv
# ≥ 32 caracteres, aleatorio
JWT_SECRET=<openssl rand -base64 48>
JWT_EXPIRATION=3600
JWT_REFRESH_EXPIRATION=604800

# ≥ 32 caracteres, aleatorio
ENCRYPTION_KEY=<openssl rand -base64 48>

POSTGRES_DB=padelpro
POSTGRES_USER=padelpro
POSTGRES_PASSWORD=<password fuerte>

SPRING_PROFILES_ACTIVE=prod

# IP/DNS público del EC2 con el puerto del backend
VITE_API_BASE_URL=http://<EC2_PUBLIC_IP>:8080

# Bootstrap del primer admin (acceso-cuenta-prod): el runner crea un ADMIN ACTIVE
# en el primer arranque si no existe ninguno. Sin estas variables no falla, pero no habrá admin.
ADMIN_EMAIL=<email del admin>
ADMIN_PASSWORD=<password fuerte ≥12>

# SMTP / Email de bienvenida (notificaciones). Proveedor inicial: Ethereal (pruebas).
# Con MAIL_HOST vacío el envío falla de forma tolerante (no bloquea la gestión de usuarios).
MAIL_HOST=smtp.ethereal.email
MAIL_PORT=587
MAIL_USERNAME=<usuario ethereal>
MAIL_PASSWORD=<password ethereal>
MAIL_FROM=no-reply@padelpro.local
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

Genera secretos con `openssl rand -base64 48`. El `.env` está cubierto por `.gitignore`.

### 2.7 Alta de los GitHub Repo Secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret        | Valor                                                       |
|---------------|------------------------------------------------------------|
| `EC2_HOST`    | IP pública (o DNS) del EC2                                  |
| `EC2_USER`    | usuario SSH (`ubuntu` en Ubuntu AMI; `ec2-user` en Amazon Linux) |
| `EC2_SSH_KEY` | **contenido completo** de la clave privada `~/.ssh/padelpro_ec2` (incluye `-----BEGIN/END-----`) |

> 🔒 Los secretos de la app (`JWT_SECRET`, etc.) **no** se dan de alta en GitHub: viven solo
> en el `.env` del EC2. El workflow solo necesita las credenciales SSH.

---

## 3. Primer deploy

1. Asegúrate de que los 3 Secrets están dados de alta (2.7) y el `.env` existe en el EC2 (2.6).
2. Haz merge de este change a `develop` (o un `push` a `develop`).
3. En la pestaña **Actions** del repo verás `CI/CD Pipeline`:
   - `build-and-test` debe quedar verde.
   - `deploy` se conecta por SSH y ejecuta el script.
4. Verifica (sección 5).

También puedes lanzarlo manualmente con **workflow_dispatch** (botón *Run workflow*) — pero
ojo: `workflow_dispatch` solo dispara `deploy` si la rama seleccionada es `develop` y el
evento se evalúa como push (el `if` exige `github.event_name == 'push'`, así que el dispatch
ejecuta build+test pero **no** deploy; para desplegar a mano usa el procedimiento de la
sección 6).

---

## 4. Operación manual en el EC2

```bash
cd ~/PadelPro

# Ver estado y salud
docker compose ps
docker compose logs -f backend     # logs en vivo
docker compose logs db | tail

# Reconstruir y levantar (lo que hace el deploy)
git pull origin develop
docker compose down
docker compose up -d --build
```

---

## 5. Verificación post-deploy (checklist)

- [ ] `docker compose ps` muestra `db`, `backend`, `frontend` en estado `running (healthy)`.
- [ ] Backend: `curl http://<EC2_PUBLIC_IP>:8080/actuator/health` → `{"status":"UP"}`.
- [ ] Flyway aplicado: `docker compose logs backend | grep -i flyway` muestra las migraciones
      aplicadas y `Successfully validated`/`Migrating`.
- [ ] Frontend: abrir `http://<EC2_PUBLIC_IP>:5173` carga la SPA.
- [ ] Login / llamada a `/api/...` desde el frontend responde (proxy de `vite preview`).
- [ ] (CI) El run de Actions quedó verde y el artefacto `jacoco-report` está disponible.

---

## 6. Rollback

El workflow es additivo: revertirlo = borrar/revertir `.github/workflows/ci.yml`.

Para revertir un **deploy concreto** en el EC2:

```bash
cd ~/PadelPro
git log --oneline -10                  # localiza el commit estable anterior
git checkout <commit-anterior>
docker compose up -d --build
# Cuando se corrija en develop:
git checkout develop && git pull && docker compose up -d --build
```

> El volumen `padelpro_postgres_data` persiste entre `down`/`up`, así que un rollback de
> código **no** borra datos. Cuidado con rollbacks que crucen una migración Flyway destructiva
> (Flyway no hace down-migrations): restaura desde backup (sección 7) si una migración cambió
> el esquema de forma incompatible.

---

## 7. Backup de la base de datos (postgres en contenedor — D2)

Como la BD vive en un contenedor (no RDS), **programa backups**. Volumen:
`padelpro_postgres_data`.

### Backup manual / por cron

```bash
# Dump comprimido con timestamp
docker compose exec -T db pg_dump -U padelpro padelpro | gzip > ~/backups/padelpro_$(date +%F_%H%M).sql.gz
```

Cron diario (en el EC2):

```bash
mkdir -p ~/backups
crontab -e
# Añade (backup diario a las 03:00, retiene 14 días):
0 3 * * * cd ~/PadelPro && docker compose exec -T db pg_dump -U padelpro padelpro | gzip > ~/backups/padelpro_$(date +\%F).sql.gz && find ~/backups -name 'padelpro_*.sql.gz' -mtime +14 -delete
```

> Recomendado: sincronizar `~/backups` a S3 (`aws s3 sync ~/backups s3://<bucket>/padelpro/`)
> para no perder los backups si se pierde la instancia.

### Restore

```bash
gunzip -c ~/backups/padelpro_<fecha>.sql.gz | docker compose exec -T db psql -U padelpro -d padelpro
```

### Evolución a RDS (futuro)

D2 fija postgres en contenedor para v1 (paridad con la referencia). Migración a **AWS RDS**
recomendada cuando los datos sean críticos (fiscales: reservas/pagos): aporta backups
automáticos, point-in-time recovery y HA. La migración consistiría en: provisionar RDS,
`pg_dump`/`pg_restore` del volumen actual, y apuntar `SPRING_DATASOURCE_URL` al endpoint RDS
en el `.env` (eliminando el servicio `db` del compose).

---

## 8. Resumen de variables y secretos

| Dónde            | Variable                                   | Uso                                   |
|------------------|--------------------------------------------|---------------------------------------|
| GitHub Secrets   | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`      | conexión SSH del job `deploy`         |
| `.env` del EC2   | `JWT_SECRET`, `ENCRYPTION_KEY`             | seguridad de la app                   |
| `.env` del EC2   | `POSTGRES_PASSWORD`, `POSTGRES_DB/USER`    | credenciales BD (db + datasource)     |
| `.env` del EC2   | `SPRING_PROFILES_ACTIVE=prod`              | perfil productivo (Flyway validate)   |
| `.env` del EC2   | `VITE_API_BASE_URL`                        | URL del backend para el frontend      |
| `.env` del EC2   | `PUBLIC_HOST` *(modo TLS)*                  | host público sslip.io/dominio para Caddy (sección 9) |

🔒 Ningún secreto vive en el repositorio ni viaja por el workflow (D4). `.gitignore` cubre
`.env`, `.env.*`, `*.env`, `*.pem`, `*.key`.

---

## 9. TLS/HTTPS en producción (change `tls-https-ec2`, Issue #203)

Antepone un reverse-proxy **Caddy** que termina TLS y sirve la app por **HTTPS** con
certificado **Let's Encrypt automático**, usando un dominio **`sslip.io`** derivado de la
IP fija — **sin comprar dominio**. Cierra el gap de *secure context* del navegador (causa del
bug #201, `crypto.randomUUID`) y el transporte en claro de JWT/credenciales.

> Artefactos: `docker-compose.prod.yml` (stack de prod autocontenido) + `caddy/Caddyfile`.
> **Este cambio se aplica manualmente en el EC2**; no lo despliega la CI automáticamente
> (así se evita romper prod antes de tener Elastic IP + `PUBLIC_HOST` listos).

### 9.1 Requisitos previos (una sola vez)

1. **Elastic IP fija** asociada a la instancia (imprescindible: si la IP cambia, el
   certificado y el host `sslip.io` dejan de valer).
   - EC2 → *Elastic IPs* → *Allocate* → *Associate* a la instancia.
2. **Abrir puertos 80 y 443** en el Security Group (ver sección 2.3). El **80 es
   obligatorio** para el reto ACME HTTP-01 de Let's Encrypt.
3. Calcular el host `sslip.io` a partir de la Elastic IP sustituyendo los puntos por guiones:
   - IP `16.192.61.61` → `PUBLIC_HOST=16-192-61-61.sslip.io`

### 9.2 Configurar `PUBLIC_HOST` en el `.env` del EC2

```bash
# En el .env del EC2 (junto a JWT_SECRET, etc.)
echo "PUBLIC_HOST=16-192-61-61.sslip.io" >> .env   # usa TU Elastic IP guionizada
```

### 9.3 Levantar el stack con TLS

```bash
cd ~/PadelPro
git pull origin develop

# Detener el stack HTTP legacy si estuviera arriba
docker compose down

# Levantar el stack de producción con TLS (Caddy + front/back sin puertos al host)
docker compose -f docker-compose.prod.yml up -d --build

# Ver la emisión del certificado (primera vez tarda unos segundos)
docker compose -f docker-compose.prod.yml logs -f caddy
```

> 💡 **Prueba primero con el *staging* de Let's Encrypt** para no gastar el rate-limit de
> producción si algo falla. Añade en `caddy/Caddyfile`, dentro del bloque de host, la
> directiva global `acme_ca https://acme-staging-v02.api.letsencrypt.org/directory`
> (o vía bloque global) y, una vez validado, quítala y `docker compose ... up -d` de nuevo.
> Los certs de staging NO son de confianza (el navegador avisará), pero confirman el flujo.

### 9.4 Verificación

```bash
# HTTPS sirve el frontend
curl -sSI https://16-192-61-61.sslip.io/ | head -1        # 200

# La API responde por el mismo origen HTTPS
curl -sS  https://16-192-61-61.sslip.io/actuator/health   # {"status":"UP"}

# HTTP redirige a HTTPS
curl -sSI http://16-192-61-61.sslip.io/ | grep -i location # https://...
```

En el navegador, abrir `https://<PUBLIC_HOST>/`, iniciar sesión y **crear una reserva**:
con HTTPS `crypto.randomUUID` funciona y el flujo se completa (regresión de #201).
Comprobar en DevTools → Network que **no hay peticiones a `:8080` ni a `http://`**
(sin *mixed content*).

### 9.5 Rollback

```bash
docker compose -f docker-compose.prod.yml down
docker compose up -d --build     # vuelve al stack HTTP legacy (puertos 8080/5173)
```

Los certificados quedan en el volumen `caddy_data` para el siguiente intento (no se
re-solicitan, evitando el rate-limit de Let's Encrypt).

### 9.6 Migrar a un dominio propio (futuro, opcional)

Apunta un registro A de tu dominio a la Elastic IP y cambia `PUBLIC_HOST=reservas.tuclub.com`
en el `.env`. `docker compose -f docker-compose.prod.yml up -d` y Caddy emite el certificado
del dominio real. Sin cambios en el `Caddyfile`.
