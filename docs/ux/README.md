# PadelPro · Sistema UX · Documentación de pantallas

Índice completo de las 24 pantallas del sistema de diseño de PadelPro.
Cada pantalla es un fichero HTML autónomo que enlaza a `_shared/styles.css`.

---

## Tabla resumen

| # | Nombre | Categoría | Capability | Endpoint | Permisos | Device | Archivo |
|---|--------|-----------|-----------|---------|---------|--------|---------|
| 01 | Splash & bienvenida | Auth | auth-local | — | Público | Mobile | [07-splash.html](./mockups/07-splash.html) |
| 02 | Login | Auth | auth-local | POST /api/v1/auth/login | Público | Mobile | [01-login.html](./mockups/01-login.html) |
| 03 | Crear cuenta | Auth | auth-local, usuarios | POST /api/v1/auth/register | Público | Mobile | [08-crear-cuenta.html](./mockups/08-crear-cuenta.html) |
| 04 | Recuperar contraseña | Auth | auth-local | POST /api/v1/auth/forgot-password | Público | Mobile | [09-recuperar-password.html](./mockups/09-recuperar-password.html) |
| 05 | Nueva contraseña | Auth | auth-local | POST /api/v1/auth/reset-password | Token reset | Mobile | [10-nueva-password.html](./mockups/10-nueva-password.html) |
| 06 | Home jugador | Reservas | reservas, disponibilidad-pistas | GET /api/v1/reservas/mias | JUGADOR | Mobile | [02-home-jugador.html](./mockups/02-home-jugador.html) |
| 07 | Buscar disponibilidad | Reservas | disponibilidad-pistas | GET /api/v1/pistas/disponibilidad | JUGADOR / INVITADO | Mobile | [03-buscar-disponibilidad.html](./mockups/03-buscar-disponibilidad.html) |
| 08 | Confirmar reserva | Reservas | reservas, pagos-redsys | POST /api/v1/reservas | JUGADOR | Mobile | [04-confirmar-reserva.html](./mockups/04-confirmar-reserva.html) |
| 09 | Checkout Redsys | Pagos | pagos-redsys | POST /api/v1/pagos/iniciar | JUGADOR | Mobile | [11-checkout-redsys.html](./mockups/11-checkout-redsys.html) |
| 10 | Pago confirmado | Pagos | pagos-redsys, reservas, notificaciones | Webhook callback | JUGADOR | Mobile | [12-pago-confirmado.html](./mockups/12-pago-confirmado.html) |
| 11 | Mis reservas | Reservas | reservas | GET /api/v1/reservas/mias | JUGADOR | Mobile | [05-mis-reservas.html](./mockups/05-mis-reservas.html) |
| 12 | Detalle de reserva | Reservas | reservas | GET /api/v1/reservas/{id} | JUGADOR | Mobile | [13-detalle-reserva.html](./mockups/13-detalle-reserva.html) |
| 13 | Mi perfil | Perfil | usuarios, auth-otp-telegram | GET /api/v1/users/me | JUGADOR | Mobile | [14-mi-perfil.html](./mockups/14-mi-perfil.html) |
| 14 | Vincular Telegram | Telegram | auth-otp-telegram | POST /api/v1/telegram/link/initiate | JUGADOR | Mobile | [15-vincular-telegram.html](./mockups/15-vincular-telegram.html) |
| 15 | Introducir OTP | Telegram | auth-otp-telegram | POST /api/v1/telegram/link/verify | JUGADOR | Mobile | [16-otp-telegram.html](./mockups/16-otp-telegram.html) |
| 16 | Partidas abiertas | Partidas | partidas | GET /api/v1/partidas/abiertas | JUGADOR | Mobile | [21-partidas-abiertas.html](./mockups/21-partidas-abiertas.html) |
| 17 | Crear partida pública | Partidas | partidas | POST /api/v1/partidas | JUGADOR | Mobile | [20-crear-partida.html](./mockups/20-crear-partida.html) |
| 18 | Confirmar unión | Partidas | partidas, pagos-redsys | POST /api/v1/partidas/{id}/unirse | JUGADOR | Mobile | [22-confirmar-union.html](./mockups/22-confirmar-union.html) |
| 19 | Mis datos y privacidad | RGPD | exportaciones-rgpd | POST /api/v1/users/me/export | JUGADOR | Mobile | [23-mis-datos.html](./mockups/23-mis-datos.html) |
| 20 | Confirmar eliminación | RGPD | exportaciones-rgpd, auditoria | DELETE /api/v1/users/me | JUGADOR | Mobile | [24-eliminar-cuenta.html](./mockups/24-eliminar-cuenta.html) |
| 21 | Dashboard club | Admin | administracion-club | GET /api/v1/admin/dashboard | MANAGER_CLUB · ADMIN | Desktop | [06-dashboard-club.html](./mockups/06-dashboard-club.html) |
| 22 | Calendario semanal | Admin | reservas, disponibilidad-pistas | GET /api/v1/admin/reservas?semana=N | MANAGER_CLUB · ADMIN | Desktop | [18-calendario-semanal.html](./mockups/18-calendario-semanal.html) |
| 23 | Reservas del club | Admin | reservas, pagos-redsys | GET /api/v1/admin/reservas | MANAGER_CLUB · ADMIN | Desktop | [19-reservas-club.html](./mockups/19-reservas-club.html) |
| 24 | Gestión de pistas | Admin | pistas | GET /api/v1/admin/pistas | MANAGER_CLUB · ADMIN | Desktop | [17-gestion-pistas.html](./mockups/17-gestion-pistas.html) |

---

## Pantallas por categoría

### Auth & Onboarding (01–05)

#### 01 · Splash & bienvenida
- **Archivo:** [mockups/07-splash.html](./mockups/07-splash.html)
- **Capability:** auth-local
- **Endpoint:** —
- **Permisos:** Público
- **Device:** Mobile
- **Descripción:** Pantalla de bienvenida de pantalla completa con el glifo PP en modo oscuro y esfera lima. Anclaje de marca previo al login.

#### 02 · Login
- **Archivo:** [mockups/01-login.html](./mockups/01-login.html)
- **Capability:** auth-local
- **Endpoint:** POST /api/v1/auth/login
- **Permisos:** Público
- **Device:** Mobile
- **Descripción:** Formulario de inicio de sesión con email y contraseña. Links a registro y recuperación. Diseño limpio con CTA lima prominente.

#### 03 · Crear cuenta
- **Archivo:** [mockups/08-crear-cuenta.html](./mockups/08-crear-cuenta.html)
- **Capability:** auth-local, usuarios
- **Endpoint:** POST /api/v1/auth/register
- **Permisos:** Público
- **Device:** Mobile
- **Descripción:** Formulario de registro con nombre, apellido, email, teléfono, contraseña y checkbox RGPD. Campos marcados como obligatorios.

#### 04 · Recuperar contraseña
- **Archivo:** [mockups/09-recuperar-password.html](./mockups/09-recuperar-password.html)
- **Capability:** auth-local
- **Endpoint:** POST /api/v1/auth/forgot-password
- **Permisos:** Público
- **Device:** Mobile
- **Descripción:** Formulario de recuperación por email. Estado de éxito con mensaje de confirmación tras envío.

#### 05 · Nueva contraseña
- **Archivo:** [mockups/10-nueva-password.html](./mockups/10-nueva-password.html)
- **Capability:** auth-local
- **Endpoint:** POST /api/v1/auth/reset-password
- **Permisos:** Token reset
- **Device:** Mobile
- **Descripción:** Formulario de nueva contraseña con barra de fortaleza y lista de requisitos visuales (longitud, mayúscula, número, símbolo).

---

### Reservas · Jugador (06–12)

#### 06 · Home jugador
- **Archivo:** [mockups/02-home-jugador.html](./mockups/02-home-jugador.html)
- **Capability:** reservas, disponibilidad-pistas
- **Endpoint:** GET /api/v1/reservas/mias
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Pantalla principal del jugador. Muestra la próxima reserva destacada, botón de buscar pista y acceso rápido a reservas anteriores. Tab bar inferior con 4 secciones.

#### 07 · Buscar disponibilidad
- **Archivo:** [mockups/03-buscar-disponibilidad.html](./mockups/03-buscar-disponibilidad.html)
- **Capability:** disponibilidad-pistas
- **Endpoint:** GET /api/v1/pistas/disponibilidad
- **Permisos:** JUGADOR / INVITADO
- **Device:** Mobile
- **Descripción:** Selector de fecha + hora con franja horaria. Grid de pistas disponibles con slots de tiempo. Filtros por tipo de superficie (cristal/muro) e interior/exterior.

#### 08 · Confirmar reserva
- **Archivo:** [mockups/04-confirmar-reserva.html](./mockups/04-confirmar-reserva.html)
- **Capability:** reservas, pagos-redsys
- **Endpoint:** POST /api/v1/reservas
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Vista de detalle de la pista seleccionada con hero olive. Resumen de fecha, hora, duración e importe. Footer sticky con CTA de reserva.

#### 09 · Checkout Redsys
- **Archivo:** [mockups/11-checkout-redsys.html](./mockups/11-checkout-redsys.html)
- **Capability:** pagos-redsys
- **Endpoint:** POST /api/v1/pagos/iniciar
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Previsualización de la tarjeta guardada con número parcial. Formulario de datos de tarjeta. Branding de seguridad Redsys. CTA de pago con importe.

#### 10 · Pago confirmado
- **Archivo:** [mockups/12-pago-confirmado.html](./mockups/12-pago-confirmado.html)
- **Capability:** pagos-redsys, reservas, notificaciones
- **Endpoint:** Webhook callback
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Pantalla oscura de éxito con icono de check lima. Código de reserva, detalle de pista y hora. Acceso a "Ver reserva" y aviso de notificación Telegram.

#### 11 · Mis reservas
- **Archivo:** [mockups/05-mis-reservas.html](./mockups/05-mis-reservas.html)
- **Capability:** reservas
- **Endpoint:** GET /api/v1/reservas/mias
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Listado de reservas del jugador con tabs "Próximas" y "Pasadas". Cards con estado (confirmada/cancelada), pista, fecha, hora e importe. Acceso al detalle de cada reserva.

#### 12 · Detalle de reserva
- **Archivo:** [mockups/13-detalle-reserva.html](./mockups/13-detalle-reserva.html)
- **Capability:** reservas
- **Endpoint:** GET /api/v1/reservas/{id}
- **Permisos:** JUGADOR (propias)
- **Device:** Mobile
- **Descripción:** Detalle completo de una reserva con hero oscuro. Código único, jugadores confirmados (chips de avatar), grid de info-pills (horario, pista, superficie, importe). Botón de cancelación.

---

### Perfil & Telegram (13–15)

#### 13 · Mi perfil
- **Archivo:** [mockups/14-mi-perfil.html](./mockups/14-mi-perfil.html)
- **Capability:** usuarios, auth-otp-telegram
- **Endpoint:** GET /api/v1/users/me
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Perfil con avatar inicial, nombre y email. Tres secciones: Cuenta (datos + seguridad), Conexiones (Telegram vinculado + métodos de pago), Privacidad (RGPD).

#### 14 · Vincular Telegram
- **Archivo:** [mockups/15-vincular-telegram.html](./mockups/15-vincular-telegram.html)
- **Capability:** auth-otp-telegram
- **Endpoint:** POST /api/v1/telegram/link/initiate
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Flujo de vinculación en 3 pasos numerados: abrir bot, enviar /vincular, introducir código. Icono azul Telegram, nombre del bot en monospace. CTA "Abrir Telegram".

#### 15 · Introducir OTP
- **Archivo:** [mockups/16-otp-telegram.html](./mockups/16-otp-telegram.html)
- **Capability:** auth-otp-telegram
- **Endpoint:** POST /api/v1/telegram/link/verify
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** 6 cajas individuales para el código OTP. Estado activo con cursor animado. Timer de caducidad en monospace. Link de reenvío de código.

---

### Partidas / Fase 2 (16–18)

#### 16 · Partidas abiertas
- **Archivo:** [mockups/21-partidas-abiertas.html](./mockups/21-partidas-abiertas.html)
- **Capability:** partidas
- **Endpoint:** GET /api/v1/partidas/abiertas
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Listado de partidas que buscan jugador. Tabs Hoy / Mañana / Semana. Cards con hora, nivel (coloreado), avatares de jugadores y huecos vacíos, precio por jugador, botón "Unirme".

#### 17 · Crear partida pública
- **Archivo:** [mockups/20-crear-partida.html](./mockups/20-crear-partida.html)
- **Capability:** partidas
- **Endpoint:** POST /api/v1/partidas
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Formulario de creación en 3 secciones: reserva base (card oscura), tipo (switches público + pago compartido), nivel (grid de 4 opciones: Inicia, Medio, Avanz., Pro).

#### 18 · Confirmar unión
- **Archivo:** [mockups/22-confirmar-union.html](./mockups/22-confirmar-union.html)
- **Capability:** partidas, pagos-redsys
- **Endpoint:** POST /api/v1/partidas/{id}/unirse
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Detalle de la partida con layout resdet. Jugadores confirmados + hueco "Tú". Grid de info-pills con horario, nivel, tu parte y total. Nota de política de cancelación automática.

---

### RGPD & Privacidad (19–20)

#### 19 · Mis datos y privacidad
- **Archivo:** [mockups/23-mis-datos.html](./mockups/23-mis-datos.html)
- **Capability:** exportaciones-rgpd
- **Endpoint:** POST /api/v1/users/me/export · DELETE /api/v1/users/me
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** 3 bloques RGPD con referencia al artículo legal: descargar datos (Art. 15), editar datos (Art. 16), eliminar cuenta (Art. 17). Icono rojo para la acción destructiva.

#### 20 · Confirmar eliminación
- **Archivo:** [mockups/24-eliminar-cuenta.html](./mockups/24-eliminar-cuenta.html)
- **Capability:** exportaciones-rgpd, auditoria
- **Endpoint:** DELETE /api/v1/users/me
- **Permisos:** JUGADOR autenticado
- **Device:** Mobile
- **Descripción:** Card de advertencia negro+rojo. Lista de consecuencias explícitas. Campo de confirmación tipo-tu-nombre ("ELIMINAR LAURA"). Botón rojo destructivo.

---

### Admin Club (21–24)

#### 21 · Dashboard club
- **Archivo:** [mockups/06-dashboard-club.html](./mockups/06-dashboard-club.html)
- **Capability:** administracion-club
- **Endpoint:** GET /api/v1/admin/dashboard
- **Permisos:** MANAGER_CLUB · ADMIN
- **Device:** Desktop
- **Descripción:** Panel admin con sidebar de navegación. KPIs en grid (reservas, ingresos, ocupación, cancelaciones). Timeline de próximas reservas. Sidebar lateral de pagos recientes.

#### 22 · Calendario semanal
- **Archivo:** [mockups/18-calendario-semanal.html](./mockups/18-calendario-semanal.html)
- **Capability:** reservas, disponibilidad-pistas
- **Endpoint:** GET /api/v1/admin/reservas?semana=N
- **Permisos:** MANAGER_CLUB · ADMIN
- **Device:** Desktop
- **Descripción:** Vista semanal de 7 columnas (Lun–Dom) con franjas horarias de 9:00 a 18:00. Eventos posicionados absolutamente (olive, alt-olive, lima, rojo mantenimiento). Selector de pista en topbar.

#### 23 · Reservas del club
- **Archivo:** [mockups/19-reservas-club.html](./mockups/19-reservas-club.html)
- **Capability:** reservas, pagos-redsys
- **Endpoint:** GET /api/v1/admin/reservas
- **Permisos:** MANAGER_CLUB · ADMIN
- **Device:** Desktop
- **Descripción:** Tabla de reservas con filter-chips (Todas / Confirmadas / Pendientes / Canceladas) y buscador. Columnas: código, jugador (avatar + email), día/hora, pista, importe, estado (pill). Exportar CSV.

#### 24 · Gestión de pistas
- **Archivo:** [mockups/17-gestion-pistas.html](./mockups/17-gestion-pistas.html)
- **Capability:** pistas
- **Endpoint:** GET /api/v1/admin/pistas
- **Permisos:** MANAGER_CLUB · ADMIN
- **Device:** Desktop
- **Descripción:** Grid de tarjetas de pista con icono tipo cancha, badge de estado (Activa / Mantenimiento), KPIs de ocupación y tarifa por hora. Tarjeta "+" para añadir pista nueva.

---

## Estructura de ficheros

```
docs/ux/
├── README.md                    ← este fichero
├── flujos.md                    ← 6 diagramas Mermaid de flujos de usuario
├── components.md                ← catálogo de ≥19 componentes reutilizables
├── design-tokens.md             ← paleta, tipografía, espaciado, sombras
└── mockups/
    ├── index.html               ← grid visual de 24 tarjetas
    ├── _shared/
    │   ├── styles.css           ← sistema de diseño unificado
    │   └── fonts.html           ← snippet Google Fonts
    ├── 01-login.html
    ├── 02-home-jugador.html
    ├── 03-buscar-disponibilidad.html
    ├── 04-confirmar-reserva.html
    ├── 05-mis-reservas.html
    ├── 06-dashboard-club.html
    ├── 07-splash.html
    ├── 08-crear-cuenta.html
    ├── 09-recuperar-password.html
    ├── 10-nueva-password.html
    ├── 11-checkout-redsys.html
    ├── 12-pago-confirmado.html
    ├── 13-detalle-reserva.html
    ├── 14-mi-perfil.html
    ├── 15-vincular-telegram.html
    ├── 16-otp-telegram.html
    ├── 17-gestion-pistas.html
    ├── 18-calendario-semanal.html
    ├── 19-reservas-club.html
    ├── 20-crear-partida.html
    ├── 21-partidas-abiertas.html
    ├── 22-confirmar-union.html
    ├── 23-mis-datos.html
    └── 24-eliminar-cuenta.html
```

---

## Discrepancias detectadas

Las siguientes diferencias se encontraron entre la tabla de tareas proporcionada por el orquestador y los metadatos reales de los ficheros HTML fuente. Los metadatos HTML son la fuente de verdad.

| Pantalla | Campo | Valor en tabla | Valor real (HTML) |
|----------|-------|---------------|-------------------|
| 10 (Pago confirmado) | Endpoint | POST callback | Webhook callback (no hay método POST explícito) |
| 21 (Admin calendar) | Capability tabla origen | reservas | reservas, disponibilidad-pistas |
| Admin sidebar (pantallas 17–19) | Nav items | Variable | Pantalla 17 incluye item "Política" en sidebar; pantallas 18–19 no lo incluyen |

Ninguna discrepancia afecta a capability, endpoint principal o permisos de forma material.
