# PadelPro — Resumen del proyecto y accesos de prueba

> Sistema de gestión de reservas para un club de pádel: reservas web + bot de Telegram, pagos,
> panel de administración, notificaciones (email + Telegram) y RGPD.
> Última actualización: 2026-07-20.

---

## 1. Acceso a la aplicación (AWS EC2)

| | |
|---|---|
| **URL** | `http://<IP-PÚBLICA-EC2>:5173` |
| **Protocolo** | HTTP plano (sin SSL, por decisión del proyecto) |
| **Entorno** | AWS EC2 (despliegue automático desde `develop` vía GitHub Actions) |

> ⚠️ **Sustituye `<IP-PÚBLICA-EC2>`** por la IP pública de la instancia (AWS Console → EC2 →
> Instances → *Public IPv4*). Si la instancia **no tiene Elastic IP**, esa IP cambia cada vez que
> se reinicia el EC2 — ése es el motivo típico de "de repente no accedo".

---

## 2. Usuarios de prueba

Usuarios **reales y persistentes** en la base de datos de AWS (creados en el arranque del backend,
idempotentes). Ambos están **ACTIVE**, así que pueden iniciar sesión directamente.

| Rol | Email (usuario) | Contraseña | Qué puede hacer |
|-----|-----------------|------------|------------------|
| **Administrador** | `admin.demo@padelpro.es` | `PadelDemo#Admin2026` | Todo: panel admin, gestión de usuarios, dashboard/KPIs, config del club, cobros, bloqueos de pista |
| **Jugador** | `jugador.demo@padelpro.es` | `PadelDemo#Jugador2026` | Reservar, ver/cancelar sus reservas, unirse a partidas abiertas, pagar |

> 🔒 **Nota de seguridad**: son credenciales de prueba sobre HTTP plano (viajan sin cifrar).
> Válidas para pruebas/demo. Antes de un uso real: cambiar las contraseñas (variables
> `SEED_ADMIN_PASSWORD` / `SEED_PLAYER_PASSWORD` en el `.env` del EC2), poner
> `SEED_USERS_ENABLED=false` y/o borrar estos usuarios.

### Cómo probar cada flujo

- **Como jugador**: entra → *Reservar* → elige día/hora → confirma. Revisa *Mis reservas* para
  cancelar o ver el estado. Prueba también *unirse* a una partida con plazas libres.
- **Como admin**: entra → *Gestión de usuarios* (crear/activar), *Dashboard* (ocupación/ingresos,
  export CSV), *Configuración del club*, *Cobros*, *Bloqueos de pista*.
- **Bot de Telegram** (opcional): desde *Mi perfil* vincula tu cuenta (`/vincular <código>`), luego
  usa `/reservar`, `/misreservas`, `/confirmar`, `/cancelar`, `/ayuda` en el chat del bot.

---

## 3. Hitos del proyecto

| Área | Hito | Estado |
|------|------|--------|
| **Plataforma** | Scaffolding monorepo, esquema BD (Flyway), observabilidad básica | ✅ |
| **Autenticación** | Login/registro (JWT), refresh de sesión, RBAC (ADMIN/USER), auditoría, hardening anti-fuerza-bruta | ✅ |
| **Reservas** | Disponibilidad de pista, crear reserva (pago atómico, anti-solape), UI del jugador (buscar/mis reservas/cancelar) | ✅ |
| **Partidas** | Ver y unirse a partidas abiertas (plazas libres) | ✅ |
| **Bloqueos** | Bloqueo de franjas de pista para eventos/torneos | ✅ |
| **Pagos** | Redsys online (link + webhook + efectivo), simulador de pago + panel admin de cobros | ✅ |
| **Administración** | Gestión de usuarios (alta/edición + email de bienvenida), dashboard con KPIs y export CSV, configuración del club | ✅ |
| **Notificaciones** | Emails de eventos (confirmación/cancelación/recibo), pata Telegram (avisos + grupo) | ✅ |
| **Telegram / OTP** | Vinculación de cuenta + OTP, **bot de reservas** (crear/confirmar/cancelar por comandos) | ✅ |
| **RGPD** | Exportación de datos + anonimización irreversible (derecho al olvido) | ✅ |
| **Infra / Calidad** | CI/CD + despliegue AWS EC2, tests E2E (Playwright + Telegram), cobertura backend | ✅ |
| **HTTPS/TLS** | Configuración lista (Caddy + Let's Encrypt vía sslip.io); **despliegue pospuesto** | ⏸️ Config lista, sin activar |

---

## 4. Stack técnico (resumen)

- **Backend**: Java + Spring Boot (arquitectura hexagonal por módulos), PostgreSQL, Flyway, JWT, BCrypt.
- **Frontend**: React + Vite (SPA), llamadas a `/api`.
- **Infra**: Docker Compose, AWS EC2, GitHub Actions (build + tests + E2E + deploy).
- **Integraciones**: Redsys (pagos), Telegram Bot API (OTP + bot de reservas + notificaciones), SMTP (emails).
