# PadelPro · Catálogo de componentes

Inventario de los componentes reutilizables del sistema de diseño.
Todos los estilos se definen en `mockups/_shared/styles.css`.

---

## Componentes de acción

### 1. `p-btn` — Botón primario

Botón de acción principal. Variantes por modificador de color.

**Clases:**
- `.p-btn` — base: fondo olive, texto crema, border-radius 14px, ancho completo
- `.p-btn.lime` — fondo lima (acción positiva: reservar, verificar, crear)
- `.p-btn.danger` — fondo danger rojo (eliminación de cuenta)
- `.p-btn:disabled` — opacidad reducida, cursor not-allowed

**Uso:** Pantallas 01–05, 11, 13, 16, 20, 22–24.

**Snippet:**
```html
<button class="p-btn lime">Reservar · 16€ <span class="arrow">→</span></button>
<button class="p-btn danger">Eliminar cuenta para siempre</button>
```

---

### 2. `admin-btn` — Botón admin

Botón compacto para acciones en el panel de administración.

**Clases:**
- `.admin-btn` — base: borde ink, fondo transparente, texto ink
- `.admin-btn.dark` — fondo ink, texto lime (acción primaria del topbar)

**Uso:** Pantallas 06, 17, 18, 19.

---

### 3. `join-btn` — Botón unirse a partida

Botón inline en cards de partida abierta. Fondo ink, texto lime.

**Uso:** Pantalla 21.

---

### 4. `action-btn` — Botón de acción RGPD

Botón de ancho completo dentro de bloques RGPD.

**Clases:**
- `.action-btn` — base: borde ink, texto ink
- `.action-btn.danger` — borde danger, texto danger

**Uso:** Pantalla 23.

---

## Componentes de formulario

### 5. `p-input` — Campo de texto

Campo de formulario estándar con label flotante o sobre el campo.

**Estructura:**
```html
<div class="p-field">
  <label>Email</label>
  <input class="p-input" type="email" placeholder="tu@email.com">
</div>
```

**Uso:** Pantallas 01–04, 09, 11.

---

### 6. `otp-box` — Caja OTP

Caja individual para entrada de un dígito del código de 6 cifras.

**Clases:**
- `.otp-box` — base: cuadrado 44px, borde line, fondo crema
- `.otp-box.filled` — con dígito introducido, borde ink
- `.otp-box.active` — caja actual, sombra olive, contiene `.cursor`

**Uso:** Pantalla 16.

---

### 7. `toggle-row` — Fila con switch

Fila de opción binaria con nombre, descripción y switch visual.

**Estructura:**
```html
<div class="toggle-row">
  <div>
    <div class="name">Partida pública</div>
    <div class="sub">Otros jugadores pueden unirse</div>
  </div>
  <div class="switch"></div>
</div>
```

**Uso:** Pantalla 20.

---

### 8. `level-pick` — Selector de nivel

Grid de 4 opciones de nivel de juego con número y etiqueta.

**Clases:**
- `.level-pick` — contenedor grid
- `.opt` — opción individual
- `.opt.active` — opción seleccionada (fondo ink, texto lime)

**Uso:** Pantalla 20.

---

## Componentes de navegación

### 9. `p-tabbar` / `p-tab` — Barra de navegación inferior

Tab bar fija en la parte inferior de pantallas mobile del jugador.

**Estructura:**
```html
<div class="p-tabbar">
  <div class="p-tab active">
    <svg>...</svg>
    Inicio
  </div>
  <div class="p-tab">...</div>
</div>
```

**Tabs disponibles:** Inicio, Buscar, Reservas, Perfil.

**Uso:** Pantallas 02, 05, 14, 21.

---

### 10. `admin-nav` / `admin-nav-item` — Navegación admin sidebar

Sidebar de navegación del panel de administración con secciones.

**Estructura:**
```html
<div class="admin-nav-section">Operación</div>
<div class="admin-nav">
  <div class="admin-nav-item active">
    <svg>...</svg>Dashboard
  </div>
</div>
```

**Items:** Dashboard, Calendario, Reservas, Pagos, Pistas, Usuarios, Política.

**Uso:** Pantallas 06, 17, 18, 19.

---

### 11. `partidas-tabs` / `partidas-tab` — Tabs de partidas

Selector de período temporal en la pantalla de partidas abiertas.

**Clases:** `.partidas-tabs` (contenedor), `.partidas-tab`, `.partidas-tab.active`.

**Uso:** Pantalla 21.

---

## Componentes de tarjeta

### 12. `reserva-card` — Tarjeta de reserva

Tarjeta de reserva en listado "Mis reservas". Muestra estado, pista, fecha y hora.

**Clases:**
- `.reserva-card` — base
- `.reserva-card .badge` — pill de estado (confirmada en olive, cancelada en danger)

**Uso:** Pantalla 05.

---

### 13. `partida-card` — Tarjeta de partida abierta

Tarjeta de partida pública en el listado de partidas abiertas.

**Estructura:** cabecera (hora + nivel), nombre de pista, row de avatares + huecos, footer (precio + botón).

**Sub-clases de nivel:**
- `.level.med` — nivel medio, fondo info azul
- `.level.adv` — nivel avanzado, fondo danger rojo

**Uso:** Pantalla 21.

---

### 14. `court-card` — Tarjeta de pista

Tarjeta de pista en el panel admin. Icono tipo cancha, badge de estado, KPIs de ocupación y tarifa.

**Clases:**
- `.court-card` — base
- `.court-card .badge.active` — pista activa (olive)
- `.court-card .badge.maint` — mantenimiento (warn)
- `.court-card.add` — tarjeta de "Añadir pista" (dashed, sin contenido)

**Uso:** Pantalla 17.

---

### 15. `home-next` — Card de próxima reserva

Hero card en la pantalla de inicio del jugador con la próxima reserva.

**Uso:** Pantalla 02.

---

## Componentes de datos / display

### 16. `info-pill` — Píldora de información

Píldora de dos líneas (label + valor) en el detalle de reserva y de partida.

**Estructura:**
```html
<div class="info-pill">
  <div class="lab">Horario</div>
  <div class="val">18:30 — 20:00</div>
</div>
```

**Uso:** Pantallas 13, 22.

---

### 17. `player-chip` — Chip de jugador

Chip con avatar y nombre del jugador en el detalle de reserva o partida.

**Clases:**
- `.player-chip` — jugador confirmado
- `.player-chip.empty` — hueco vacío (borde discontinuo, avatar "?")

**Uso:** Pantallas 13, 22.

---

### 18. `kpi` — Tarjeta de KPI admin

Tarjeta de métrica clave en el dashboard del club.

**Estructura:** valor principal (Bricolage Grotesque, grande) + etiqueta + variación porcentual.

**Uso:** Pantalla 06.

---

### 19. `cal-event` — Evento de calendario

Bloque de evento en la vista semanal del calendario admin. Posicionado absolutamente dentro de `.cal-col`.

**Clases:**
- `.cal-event` — reserva estándar (olive)
- `.cal-event.alt` — reserva alternativa (olive oscuro)
- `.cal-event.lime` — reserva destacada (lime)
- `.cal-event.maint` — mantenimiento (danger rojo)

**Uso:** Pantalla 18.

---

### 20. `chip` / `slot` — Slot de disponibilidad

Slot horario en la pantalla de búsqueda de disponibilidad.

**Clases:**
- `.slot` — slot disponible
- `.slot.taken` — slot ocupado
- `.slot.selected` — slot seleccionado por el usuario

**Uso:** Pantalla 03.

---

## Componentes de estado y feedback

### 21. `success-ico` — Icono de éxito

Circulo con check mark en color lima. Usado en pantalla de pago confirmado.

**Uso:** Pantalla 12.

---

### 22. `delete-warn` — Bloque de advertencia de eliminación

Card oscura con icono de alerta blanco y texto de advertencia crítica.

**Estructura:** `.delete-warn` → `.ico` (svg) + `h1` + `p`.

**Uso:** Pantalla 24.

---

### 23. `rgpd-block` — Bloque de acción RGPD

Bloque con cabecera (icono + nombre de derecho + referencia legal), descripción y botón de acción.

**Estructura:** `.rgpd-block` → `.head` (`.ico` + `.info`) + `.desc` + `.action-btn`.

**Uso:** Pantalla 23.

---

### 24. `filter-chip` — Chip de filtro

Chip de filtro tabular con contador de resultados.

**Clases:**
- `.filter-chip` — base (borde, fondo blanco)
- `.filter-chip.active` — seleccionado (fondo ink, texto lime)
- `.filter-chip .count` — badge numérico interno

**Uso:** Pantalla 19.

---

### 25. `table-head` / `table-row` — Tabla de reservas admin

Fila de cabecera y fila de datos de la tabla de reservas del club.

**Uso:** Pantalla 19.

---

## Componentes de layout

### 26. `resdet` — Layout de detalle (hero oscuro)

Layout de pantalla completa con hero dark (`.resdet-hero`) que contiene estado, título, metadatos y código; y cuerpo claro (`.resdet-body`) con secciones y footer sticky (`.resdet-footer`).

**Uso:** Pantallas 13, 22.

---

### 27. `phone` — Frame de teléfono

Contenedor del mockup mobile con notch y barra de estado.

**Sub-elementos:**
- `.phone-notch` — notch superior
- `.phone-status` / `.phone-status.dark` — barra de estado (hora + señal)
- `.phone-content` — área de scroll de contenido

---

### 28. `desktop` + `admin` — Frame de escritorio admin

Contenedor del mockup desktop. `.admin` añade el layout sidebar + main.

**Sub-elementos:** `.admin-sidebar`, `.admin-main`, `.admin-topbar`, `.admin-content`.

---

## Resumen de recuento

| Categoría | Componentes |
|-----------|-------------|
| Acción | p-btn, admin-btn, join-btn, action-btn |
| Formulario | p-input, otp-box, toggle-row, level-pick |
| Navegación | p-tabbar/p-tab, admin-nav/item, partidas-tabs |
| Tarjeta | reserva-card, partida-card, court-card, home-next |
| Datos / display | info-pill, player-chip, kpi, cal-event, chip/slot |
| Estado / feedback | success-ico, delete-warn, rgpd-block, filter-chip, table-head/row |
| Layout | resdet, phone, desktop+admin |

**Total: 28 componentes catalogados** (superando el mínimo de 19 requerido).
