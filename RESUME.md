# PadelPro — Resumen del proyecto y accesos de prueba

> Sistema de gestión de reservas para un club de pádel: reservas web + bot de Telegram, pagos,
> panel de administración, notificaciones (email + Telegram) y RGPD.

---

## 1. Acceso a la aplicación (AWS EC2)

| | |
|---|---|
| **URL** | **http://16.192.61.61:5173/** |
| **Protocolo** | HTTP plano (sin SSL, por decisión del proyecto) |
| **Entorno** | AWS EC2 (despliegue automático desde `develop` vía GitHub Actions) |

---

## 2. Usuarios de prueba

Usuarios reales creados en la base de datos de AWS. Verificado: ambos inician sesión correctamente.

| Rol | Email (usuario) | Contraseña | Estado |
|-----|-----------------|------------|--------|
| **Jugador** | `jugador@padelpro.es` | `PadelPro2026#Jugador` | ✅ ACTIVE, listo |
| **Administrador** | `admin@padelpro.es` | `PadelPro2026#Admin` | ⚠️ ACTIVE, pendiente de asignarle rol ADMIN (ahora entra como USER) |

> ⚠️ **Nota sobre el admin**: la cuenta `admin@padelpro.es` está activa y puede iniciar sesión,
> pero todavía tiene rol **USER**. Para que tenga permisos de administrador, un admin debe
> editar el usuario (id 7) en *Gestión de usuarios* → rol **ADMIN**.
>
> 🔒 **Seguridad**: credenciales de prueba sobre HTTP plano (viajan sin cifrar). Cambiar las
> contraseñas y/o borrar estos usuarios antes de un uso real.

### Cómo probar cada flujo

- **Como jugador**: entra → *Reservar* → elige día/hora → confirma. En *Mis reservas* puedes
  cancelar o ver el estado. Prueba también *unirte* a una partida con plazas libres.
- **Como admin** (una vez tenga rol ADMIN): *Gestión de usuarios* (crear/activar), *Dashboard*
  (ocupación/ingresos, export CSV), *Configuración del club*, *Cobros*, *Bloqueos de pista*.
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
| **Telegram / OTP** | Vinculación de cuenta + OTP, bot de reservas (crear/confirmar/cancelar por comandos) | ✅ |
| **RGPD** | Exportación de datos + anonimización irreversible (derecho al olvido) | ✅ |
| **Infra / Calidad** | CI/CD + despliegue AWS EC2, tests E2E (Playwright + Telegram), cobertura backend | ✅ |
| **HTTPS/TLS** | Configuración lista (Caddy + Let's Encrypt vía sslip.io); despliegue pospuesto | ⏸️ Config lista, sin activar |

---

## 4. Stack técnico (resumen)

- **Backend**: Java + Spring Boot (arquitectura hexagonal por módulos), PostgreSQL, Flyway, JWT, BCrypt.
- **Frontend**: React + Vite (SPA), llamadas a `/api`.
- **Infra**: Docker Compose, AWS EC2, GitHub Actions (build + tests + E2E + deploy).
- **Integraciones**: Redsys (pagos), Telegram Bot API (OTP + bot de reservas + notificaciones), SMTP (emails).
