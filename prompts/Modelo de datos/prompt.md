# Prompt 1

Rol: Eres un experimentado Arquitecto de Software Senior en el diseño de arquitectura de software.

Objetivo: Diseño de modelo de datos completo.Analiza toda la documentación disponible @prompts/descripcion/descripcion.md y @readme.md y, basándote sólo y exclusivamente en esta información, diseña un modelo de datos completo que incluya entidades, atributos (nombre y tipo) y relaciones. Usa el estándar Diagramas Entidad-Relación (ER). La nomenclatura de entidades y atributos deben estar en ingles.

Outputs a generar: actualiza el @readme.md genera la documentación relativa al punto 4 Modelo de datos e incluye los diagramas ER necesarios en formato Mermaid

No inventes lo que no sepas. Si necesitas solucionar dudas, pregúntame antes de realizar nada.

# Respuesta Prompt 1
Antes de diseñar el modelo, tengo **3 dudas concretas** que afectan directamente a la estructura de las tablas y que no están respondidas en la documentación:

---

### ❓ Preguntas previas

**1. Precio de la reserva**
La descripción menciona pagos e ingresos pero nunca define un modelo de precios. ¿Cómo funciona?

- **Opción A** — Precio fijo global (ej. 10€/hora) configurado por el administrador. Todas las reservas cuestan lo mismo.
- **Opción B** — Precio variable según duración (ej. 10€/h → 1h=10€, 1.5h=15€, 2h=20€...).
- **Opción C** — El link de Redsys ya lleva el importe embebido y el sistema solo registra si está pagado o no, sin almacenar el importe.

> Esto afecta a las tablas `PAYMENTS` y `SYSTEM_CONFIG` y al registro de ingresos del dashboard.
Respuesta: Opción B. Precio Variable según duración. El administrador marca el precio/hora. y se multiplica por la horas de la reserva.
---

**2. Vinculación de Telegram**
Para enviar mensajes directos a un usuario (OTP, confirmación de pago, notificaciones), Telegram requiere el `chat_id` del usuario, que es distinto al número de teléfono.

- **Opción A** — El usuario se vincula en su perfil haciendo `/start` al bot y el sistema almacena su `telegram_chat_id`.
- **Opción B** — Solo se usa el número de teléfono (que en Telegram puede buscarse si el usuario lo tiene público, pero no es fiable).
- **Opción C** — No hay mensajes directos a usuarios individuales por Telegram; solo se publica en el grupo.

> Esto determina si añadimos el campo `telegram_chat_id` a la tabla `USERS`.
Respuesta: Opción B. El usuario debe tener su telegram bien configurado.
---

**3. Historial de notificaciones**
La descripción menciona envío de notificaciones (email, Telegram) pero no especifica si se debe almacenar un registro de cada notificación enviada (aparte del log de auditoría).

- **Opción A** — Solo el log de auditoría. Una entrada de auditoría por cada notificación enviada.
- **Opción B** — Tabla `NOTIFICATION_LOG` independiente con estado de entrega (enviado/fallido), canal y destinatario.

> Opción B tiene más valor para el administrador (puede ver si una notificación llegó) pero añade complejidad.
Respuesta: Opción B. 
---

Con las respuestas a estas 3 preguntas genero el modelo de datos completo sin inventar nada. ¿Me las confirmas?

# Prompt 2 
Respuesta 1: Opción B. Precio Variable según duración. El administrador marca el precio/hora. y se multiplica por la horas de la reserva.
Respuesta 2: Opción B. El usuario debe tener su telegram bien configurado.
Respuesta 3:  Opción B. 

# Respuesta prompt 2 

✅ Sección 4 generada. Aquí el resumen completo:

---

### 📊 Modelo de Datos — Resumen

#### **8 entidades** diseñadas exclusivamente a partir de la documentación

| Entidad | Campos | Decisión clave |
|---|---|---|
| `USERS` | 11 | `phone` como identificador Telegram; roles `ADMIN`/`USER` |
| `RESERVATIONS` | 11 | Estados: `CONFIRMED` → `PAID`/`CANCELLED`/`PENDING_PAYMENT` |
| `PARTICIPANTS` | 9 | `user_id = NULL` para jugadores externos; máx. 4 por reserva |
| `PAYMENTS` | 12 | `amount = price_per_hour × (duration_minutes/60)`; `UNIQUE` por reserva |
| `OTP_CODES` | 7 | TTL 10 min; 3 tipos: reserva, cancelación, reset password |
| `AUDIT_LOG` | 9 | Inmutable; cubre WEB, TELEGRAM y acciones SYSTEM |
| `NOTIFICATION_LOG` | 12 | 3 canales: `EMAIL`, `TELEGRAM_DIRECT`, `TELEGRAM_GROUP`; estado `SENT`/`FAILED` |
| `SYSTEM_CONFIG` | 15 | Singleton (`id=1`); `price_per_hour` configurable; credenciales cifradas AES-256 |

#### **2 diagramas ER Mermaid**
- **4.2** — Modelo core: `USERS ↔ RESERVATIONS ↔ PARTICIPANTS ↔ PAYMENTS`
- **4.3** — Modelo transversal: `OTP_CODES`, `AUDIT_LOG`, `NOTIFICATION_LOG`, `SYSTEM_CONFIG`

#### **17 índices** definidos con justificación de rendimiento por tabla

#### Decisiones derivadas de las respuestas confirmadas:
- `price_per_hour` en `SYSTEM_CONFIG` → multiplicado por horas en `PAYMENTS.amount`
- `phone` en `USERS` → identificador para envío por Telegram (no se almacena `telegram_chat_id`)
- `NOTIFICATION_LOG` con estado de entrega → el admin puede ver notificaciones fallidas

¿Continuamos con el **punto 5 — Especificación de la API**?