# PadelPro — Documento de Proyecto (OpenSpec)

> Este documento es la referencia de dominio para todos los agentes que trabajan en PadelPro.
> Se genera a partir de `README.md`, `docs/PROJECT.md`, `docs/data-model.md` y `docs/security-design.md`.
> En caso de conflicto, las fuentes en `docs/` tienen precedencia sobre este fichero.

---

## 1. Qué es PadelPro

**PadelPro** es una plataforma digital integral para la gestión de reservas de pistas de pádel en instalaciones pequeñas y medianas. Combina una aplicación web moderna con integración nativa en Telegram (v1.0), eliminando los problemas de gestión manual que se producen cuando las reservas se coordinan a través de grupos de mensajería sin ningún sistema centralizado.

El sistema centraliza en un único punto la reserva de pistas, el control de disponibilidad en tiempo real, la gestión de participantes, el cobro de reservas mediante la pasarela Redsys, y el seguimiento de ingresos. Ofrece tanto una interfaz web como la posibilidad de operar directamente desde el grupo de Telegram ya existente en la comunidad de jugadores.

PadelPro está diseñado para instalaciones de una sola pista (On-Premise, v1.0), sin multi-tenancy. La arquitectura hexagonal y el modelo de datos están preparados para escalar a múltiples pistas o instalaciones en fases posteriores, pero esta capacidad no forma parte del alcance actual.

---

## 2. Problema que resuelve

Las instalaciones deportivas pequeñas (comunidades de vecinos, clubes de 1-4 pistas, grupos informales) coordinan sus reservas a través de grupos de WhatsApp o Telegram sin un sistema centralizado. Esto genera tres problemas críticos:

1. **Solapamientos de reservas**: sin un calendario único y en tiempo real, dos usuarios pueden reservar la misma franja sin que el sistema lo detecte.
2. **Falta de registro económico**: los pagos se realizan en efectivo sin trazabilidad digital, lo que impide al administrador conocer sus ingresos reales.
3. **Dependencia de una persona gestora**: la sincronización entre canales (mensajería + app de pago externa) recae en una sola persona, creando un punto de fallo único.

PadelPro resuelve los tres problemas con una única plataforma que actúa como fuente de verdad para reservas, pagos y comunicaciones.

---

## 3. Usuarios y roles

### Roles en v1.0

| Rol | Descripción | Acceso |
|---|---|---|
| `ADMIN` | Administrador del club. Gestiona usuarios, reservas, pagos y configuración del sistema. | Acceso total. Endpoints `/api/admin/**` exclusivos. |
| `USER` | Jugador registrado. Gestiona sus propias reservas y pagos. | Acceso a sus propios recursos. |

> **Roles planificados para Fase 2+** (no implementados en v1.0):
> - `MANAGER_CLUB`: responsable del club con subconjunto de capacidades de ADMIN.
> - `INVITADO`: acceso público de solo lectura para consultar disponibilidad sin autenticación.

### Actor especial: Usuario Casual

El jugador casual (sin cuenta en la plataforma) puede incorporarse a una reserva como participante respondiendo al mensaje del grupo de Telegram. **No tiene acceso a la API web** y no necesita rol en el sistema. Sus datos se almacenan como `external_name` y `external_phone` en la tabla `participants`.

---

## 4. Glosario del dominio

| Término | Definición |
|---|---|
| **Pista** | Instalación física de pádel disponible para reservar. En v1.0, el sistema gestiona una única pista. |
| **Reserva** | Asignación de una franja horaria de la pista a un titular. Tiene estado (`PENDING_CONFIRMATION`, `CONFIRMED`, `COMPLETED`, `CANCELLED`) y un pago asociado. |
| **Franja horaria** | Periodo de tiempo con hora de inicio y duración (mínimo 60 min, máximo 180 min, en intervalos de 30 min). |
| **Titular** | Usuario registrado (`USER` o `ADMIN`) que crea la reserva. Es el único responsable del pago. |
| **Participante** | Persona asignada a una reserva como jugador. Puede ser un usuario registrado o un jugador casual externo. Máximo 4 por reserva. |
| **Jugador casual** | Persona sin cuenta en la plataforma que participa en una reserva a través del grupo de Telegram. |
| **Partida pública** | Reserva con huecos libres (menos de 4 participantes), visible para que otros usuarios puedan unirse. |
| **Club** | La instalación deportiva que usa PadelPro. En v1.0 es una instalación única (single-tenant). |
| **Política de cancelación** | Número mínimo de horas de antelación requeridas para cancelar una reserva sin coste. Configurable por el ADMIN en `system_config`. |
| **Vinculación Telegram** | Proceso por el que un usuario asocia su cuenta de PadelPro con su chat personal de Telegram, habilitando la recepción de OTPs y notificaciones. |
| **OTP** | Código de un solo uso (6 dígitos, TTL 10 min, máximo 3 intentos) enviado al Telegram personal del usuario para confirmar operaciones críticas. Se almacena como SHA-256. |
| **Inscripción** | Acción de un usuario de unirse a una reserva con huecos disponibles. |
| **Pago Redsys** | Transacción de pago online procesada mediante la pasarela bancaria Redsys (HMAC SHA-256). PadelPro nunca almacena datos de tarjeta. |
| **Pago en efectivo** | Pago físico registrado por el ADMIN en el sistema, sin pasarela. |
| **Disponibilidad** | Estado de las franjas horarias de una fecha concreta: libres u ocupadas. |
| **Configuración del club** | Parámetros globales del sistema (precio por hora, horario de apertura/cierre, máximo de participantes, política de cancelación, divisa, zona horaria). Singleton en BD (`system_config`, id=1). |
| **Anonimización RGPD** | Proceso de borrado lógico que sustituye los datos personales identificativos de un usuario por valores anónimos, preservando el historial de reservas y pagos por obligación fiscal. |
| **Webhook Redsys** | Llamada HTTP POST que Redsys envía al backend para notificar el resultado de una transacción. Validado por HMAC SHA-256. |
| **Webhook Telegram** | Llamada HTTP POST que Telegram envía al backend con cada mensaje del bot. Validado por el header `X-Telegram-Bot-Api-Secret-Token`. |

---

## 5. Capabilities

| ID | Nombre | Fase | Descripción | Spec |
|---|---|---|---|---|
| `auth-local` | Autenticación local | Fase 1 | Registro, login, refresh token, logout y recuperación de contraseña con JWT HS256. | [spec](specs/auth-local/spec.md) |
| `auth-otp-telegram` | Autenticación OTP Telegram | Fase 1 | Vinculación de cuenta con Telegram, generación y verificación de OTP para operaciones críticas. | [spec](specs/auth-otp-telegram/spec.md) |
| `usuarios` | Gestión de usuarios | Fase 1 | Perfil propio, gestión administrativa por ADMIN, RGPD básico. | [spec](specs/usuarios/spec.md) |
| `roles-permisos` | Roles y permisos | Fase 1 | Matriz RBAC (ADMIN/USER), protección de endpoints, asignación de roles. | [spec](specs/roles-permisos/spec.md) |
| `configuracion-club` | Configuración del club | Fase 1 | Parámetros globales del sistema (singleton `system_config`). | [spec](specs/configuracion-club/spec.md) |
| `pistas` | Gestión de pistas | Fase 1 | Consulta de disponibilidad horaria de la pista. | [spec](specs/pistas/spec.md) |
| `reservas` | Gestión de reservas | Fase 1 | Ciclo de vida completo de una reserva: creación, consulta, cancelación, unión de participantes. | [spec](specs/reservas/spec.md) |
| `partidas` | Partidas y participantes | Fase 1 | Gestión de participantes en una reserva, partidas públicas con huecos. | [spec](specs/partidas/spec.md) |
| `pagos-redsys` | Pagos Redsys | Fase 1 | Inicio de pago online, webhook de confirmación, idempotencia y pago en efectivo. | [spec](specs/pagos-redsys/spec.md) |
| `disponibilidad-pistas` | Disponibilidad de pistas | Fase 1 | Consulta de franjas libres/ocupadas para una fecha. | [spec](specs/disponibilidad-pistas/spec.md) |
| `notificaciones` | Notificaciones | Fase 1 | Envío de mensajes por Telegram y email (confirmaciones, OTPs, avisos). | [spec](specs/notificaciones/spec.md) |
| `auditoria` | Auditoría | Fase 1 | Registro inmutable de todas las acciones del sistema en `audit_log`. | [spec](specs/auditoria/spec.md) |
| `exportaciones-rgpd` | Exportaciones RGPD | Fase 1 | Anonimización de usuarios, acceso a datos propios, derechos del titular. | [spec](specs/exportaciones-rgpd/spec.md) |
| `administracion-club` | Administración de club | Fase 2 | Dashboard de gestión, métricas, informes. Sin superficie REST en v1.0. | — |

---

## 6. Fases del producto

### Fase 1 — MVP (activa)

**Objetivo:** Sistema operativo con reservas, pagos Redsys y bot Telegram básico.

Capabilities incluidas: `auth-local`, `auth-otp-telegram`, `usuarios`, `roles-permisos`, `configuracion-club`, `pistas`, `reservas`, `partidas`, `pagos-redsys`, `disponibilidad-pistas`, `notificaciones`, `auditoria`, `exportaciones-rgpd`.

Canal de mensajería: **Telegram Bot API** (WhatsApp Business API diferida a Fase 2).
Instalación: **una sola pista** (sin multi-tenancy).
Despliegue: **On-Premise** con Docker Compose.

### Fase 2 — Dashboard y partidas avanzadas (planificada)

**Objetivo:** Dashboard de gestión para MANAGER_CLUB, partidas avanzadas, integración WhatsApp Business API.

Capabilities nuevas: `administracion-club`.
Roles nuevos: `MANAGER_CLUB`.

### Fase 3 — Multi-club y federaciones (planificada)

**Objetivo:** Soporte multi-tenancy, federaciones de clubes.

---

## 7. Flujos críticos

Los siguientes flujos tienen cobertura de tests obligatoria del 100% (unit + integration):

### FC-01: Reserva y pago online

```
Usuario autenticado
  → Consulta disponibilidad (GET /api/reservas/disponibles?fecha=X)
  → Crea reserva (POST /api/reservas) → 201 + estado PENDING_CONFIRMATION
  → Inicia pago (POST /api/pagos/iniciar) → URL TPV Redsys
  → Usuario completa pago en Redsys
  → Redsys envía webhook (POST /api/pagos/webhook) → validación HMAC → 200
  → Reserva pasa a CONFIRMED + pago a PAID
  → Notificación Telegram al titular
```

### FC-02: Vinculación de cuenta con Telegram y OTP

```
Usuario autenticado (web)
  → Solicita vinculación (PATCH /api/usuarios/me con telegramLink)
  → Backend genera OTP tipo TELEGRAM_LINK → envía al chat personal del bot
  → Usuario envía /vincular XXXXXX al bot @PadelProBot
  → Webhook Telegram validado por X-Telegram-Bot-Api-Secret-Token
  → Backend valida OTP → UPDATE users SET telegram_chat_id
  → Usuario puede recibir OTPs y notificaciones por Telegram
```

### FC-03: Eliminación RGPD (anonimización)

```
ADMIN
  → DELETE /api/admin/usuarios/{id}
  → Anonimización: first_name, last_name, phone, email, telegram_chat_id sustituidos
  → Reservas y pagos preservados (obligación fiscal 5 años)
  → Entrada en audit_log con acción USER_ANONYMIZED
  → No se permite hard delete
```

### FC-04: Cancelación de reserva dentro del plazo

```
Titular autenticado
  → DELETE /api/reservas/{id}
  → Sistema verifica: owner_id == authenticatedUserId
  → Sistema verifica: ahora + cancellation_deadline_hours <= start_time
  → Reserva pasa a CANCELLED + pago a CANCELLED
  → Notificación al grupo Telegram
  → Entrada en audit_log con acción RESERVATION_CANCELLED
```

---

## 8. Reglas de negocio

Las reglas marcadas con (*) deben añadirse a `docs/security-design.md`.

| Código | Dominio | Descripción |
|---|---|---|
| RN-AUTH-01 | Autorización | Un USER solo puede ver una reserva si es `owner_id` o aparece en `participants`. |
| RN-AUTH-02 | Autorización | Un USER solo puede cancelar su propia reserva (`owner_id = authenticatedUserId`). |
| RN-AUTH-03 | Autorización | Un USER no puede unirse dos veces a la misma reserva. |
| RN-AUTH-04 | Autorización | Un USER solo puede iniciar pago de una reserva de la que es `owner_id`. |
| RN-AUTH-05 | Autorización | Un ADMIN no puede desactivarse a sí mismo. |
| RN-AUTH-06 (*) | Autenticación | Bloqueo tras 10 fallos en 10 min → 15 min de bloqueo. Se aplica por IP y por usuario de forma independiente. |
| RN-AUTH-07 (*) | OTP | OTP de 6 dígitos, TTL 10 min, máximo 3 intentos, almacenado como SHA-256, de un solo uso. |
| RN-AUTH-08 (*) | Contraseñas | Contraseña: mínimo 8 caracteres + 1 mayúscula + 1 número, máximo 128 caracteres, BCrypt cost 12. |
| RN-AUTH-09 (*) | Tokens | Access token 15 min en memoria JavaScript; refresh token 7 días en cookie httpOnly. |
| RN-RES-01 (*) | Reservas | No se permite solapamiento de franjas para la misma pista. Implementado con `SELECT FOR UPDATE`. |
| RN-RES-02 (*) | Reservas | El máximo de participantes por reserva lo determina `system_config.max_participants` (por defecto 4). |
| RN-RES-03 (*) | Reservas | El precio se calcula solo en backend; el importe enviado por el cliente es ignorado. |
| RN-RES-04 (*) | Reservas | La ventana de reserva anticipada y la política de cancelación provienen de `system_config`. |
| RN-RES-05 (*) | Reservas | Idempotencia: POST con misma `Idempotency-Key` devuelve la reserva existente sin crear duplicado. |
| RN-PAY-01 (*) | Pagos | El webhook de Redsys debe validarse con HMAC SHA-256 antes de procesar cualquier campo. |
| RN-PAY-02 (*) | Pagos | Idempotencia de pagos: `redsys_order_id` UNIQUE; webhook duplicado con `status=PAID` se ignora. |
| RN-PAY-03 (*) | Pagos | Datos de tarjeta (PAN, CVV, fecha de caducidad) nunca se almacenan ni se registran en logs. |
| RN-PAY-04 (*) | Pagos | Las transiciones de estado del pago son unidireccionales: `PENDING→IN_PROGRESS→PAID`. Sin reversión. |
| RN-TEL-01 (*) | Telegram | El webhook de Telegram valida `X-Telegram-Bot-Api-Secret-Token` antes de procesar cualquier update. |
| RN-TEL-02 (*) | Telegram | La vinculación de cuenta requiere OTP confirmado vía Telegram (TTL 10 min). |
| RN-TEL-03 (*) | Telegram | Solo las cuentas con `telegram_chat_id NOT NULL` reciben notificaciones por Telegram. |
| RN-RGPD-01 (*) | RGPD | La eliminación de usuario es anonimización (no hard delete); genera entrada en `audit_log`. |
| RN-RGPD-02 (*) | RGPD | Datos financieros: retención mínima 5 años. Logs de auditoría: retención mínima 2 años. |
| RN-RGPD-03 (*) | RGPD | Las respuestas de error no exponen datos personales de otros usuarios. |
| RN-RGPD-04 (*) | RGPD | Los logs no contienen contraseñas, tokens JWT, códigos OTP ni datos de tarjeta. |
| RN-SEC-01 (*) | Seguridad | Rate limiting: login 5/min/IP, registro 3/min/IP, solicitud de reset 3/min/IP. |
| RN-SEC-02 (*) | Seguridad | Los secretos del sistema (`redsys_secret_key`, `telegram_bot_token`, `smtp_password`) se cifran con AES-256-GCM en BD. |
