# PadelPro · Design Tokens

Referencia completa de los tokens del sistema de diseño.
Todos los tokens se definen como custom properties CSS en `:root` dentro de `mockups/_shared/styles.css`.

---

## Paleta de colores

### Colores base (backgrounds e ink)

| Token | Valor hex | Uso |
|-------|-----------|-----|
| `--bg-cream` | `#FAFAF5` | Fondo de pantallas mobile y contenido principal |
| `--bg-warm` | `#F4F2EA` | Fondo de la aplicación mockup (stage background) |
| `--ink` | `#0A0A0A` | Texto principal, fondos de hero, botones primarios oscuros |
| `--ink-soft` | `#2A2A28` | Separadores sobre fondo oscuro, bordes de nav |

### Texto muted

| Token | Valor hex | Uso |
|-------|-----------|-----|
| `--muted` | `#6B6B66` | Labels secundarios, subtítulos, texto descriptivo |
| `--muted-light` | `#A8A8A0` | Placeholder, metadata, números de línea horaria |

### Bordes y líneas

| Token | Valor hex | Uso |
|-------|-----------|-----|
| `--line` | `#E5E3D8` | Bordes de cards, separadores de sección, tablas |
| `--line-soft` | `#EFEDE3` | Separadores sutiles, divisiones internas de cards |

### Colores de marca

| Token | Valor hex | Descripción | Uso |
|-------|-----------|-------------|-----|
| `--olive` | `#3F4A2E` | Verde oscuro tierra · color primario de marca | Fondos hero, eventos de calendario, botones `p-btn` base |
| `--olive-dark` | `#2A3220` | Variante más oscura de olive | Hover states, eventos alternos en calendario |
| `--lime` | `#C8FF3D` | Verde lima fluorescente · acento primario | CTAs positivos (`p-btn.lime`), nav activo, dot de marca, badges de capability |
| `--lime-soft` | `#E8FFA0` | Lima suave | Fondos de badges de estado "vinculado", highlights |

### Colores semánticos

| Token | Valor hex | Significado | Uso |
|-------|-----------|-------------|-----|
| `--danger` | `#C7472D` | Error, destrucción, riesgo alto | Botón eliminar cuenta, mantenimiento en calendario, icono RGPD eliminar |
| `--warn` | `#E8A33C` | Advertencia, pendiente de acción | Pill "Pago pendiente" en tabla de reservas admin |
| `--info` | `#4A6FA5` | Información, contexto externo (Telegram) | Icono y avatar Telegram, nivel "medio" en partidas |

---

## Tipografía

### Familias

| Familia | Fuente | Uso |
|---------|--------|-----|
| `'Bricolage Grotesque'` | Google Fonts | Display, títulos principales, números grandes (KPIs, hora) |
| `'Geist'` | Google Fonts | Cuerpo de texto, labels, UI general |
| `'Geist Mono'` | Google Fonts | Datos estructurados, códigos de reserva, timers OTP, metadata técnica |

**Fallbacks:** `sans-serif` para Bricolage Grotesque y Geist; `monospace` para Geist Mono.

**Subsets importados:**
- Bricolage Grotesque: `opsz` 12–96, pesos 400/500/600/700/800
- Geist: pesos 300/400/500/600/700
- Geist Mono: pesos 400/500

### Escala de tamaños

| Uso | Tamaño | Familia | Peso | Letter-spacing |
|-----|--------|---------|------|---------------|
| Stage title (mockup) | 42px | Bricolage Grotesque | 700 | -0.03em |
| Título H1 mobile | 28–32px | Bricolage Grotesque | 800 | -0.03em |
| Título H1 admin desktop | 22–24px | Bricolage Grotesque | 700 | -0.02em |
| Número KPI grande | 28–32px | Bricolage Grotesque | 800 | -0.03em |
| Número de hora (partida) | 24px | Bricolage Grotesque | 700 | -0.02em |
| Subtítulo H2/H3 | 13–15px | Geist | 600 | — |
| Cuerpo de texto | 13–14px | Geist | 400 | — |
| Label pequeño | 11–12px | Geist | 400/500 | — |
| Código / monospace | 11–13px | Geist Mono | 400/500 | 0.04–0.1em |
| Micro label (metadata) | 9–10px | Geist Mono | 400 | 0.06–0.12em |

### Line heights

| Contexto | Valor |
|----------|-------|
| Títulos display | `1` / `1.1` |
| Cuerpo de texto | `1.5` |
| Descripciones largas | `1.6` |

---

## Espaciado

El sistema de espaciado es libre (no usa escala de tokens nombrados), pero los valores recurrentes son:

| Valor | Uso habitual |
|-------|-------------|
| `4px` | Gap entre badges, separación mínima entre elementos inline |
| `6px` | Padding interno de badges pequeños |
| `8px` | Margen entre label y valor, separación de iconos |
| `12px` | Gap interno de rows (profile-row, toggle-row) |
| `14px` | Padding interno de cards compactas (info-pill, rgpd-block) |
| `16px` | Padding interno de cards móviles, gap entre secciones de perfil |
| `20px` | Margen entre secciones, gap de la grid de pistas |
| `24px` | Padding horizontal de contenido mobile, margen de sección |
| `28px` | Margen superior de elementos de formulario destacados |
| `32px` | Padding de cabeceras de stage |
| `40px` | Padding horizontal del layout desktop admin |
| `48px` | Padding superior del hero de índice |
| `60px` | Padding inferior de grids de contenido |

---

## Border radius

| Token (clase o contexto) | Valor | Uso |
|--------------------------|-------|-----|
| Phone frame | `48px` | Marco exterior del teléfono |
| Desktop frame | `12px` | Marco exterior del mockup desktop |
| Cards grandes | `24px` | Cards de hero (checkout, pago confirmado) |
| Cards estándar | `16px` | Cards de reserva, KPI, pista, tabla admin, checkout card |
| Cards de partida | `16px` | `partida-card` |
| Botones `p-btn` | `14px` | Botones primarios mobile |
| Botones `admin-btn` | `8px` | Botones admin |
| Info pills | `12px` | `info-pill`, `rgpd-block` |
| OTP boxes | `12px` | Cajas de código OTP |
| Badges inline | `6–8px` | Pills de estado (OK, Pago, Cancelada) |
| Dots / indicadores | `50%` | Punto de marca, avatares circulares |

---

## Sombras

| Contexto | Valor | Uso |
|----------|-------|-----|
| Phone frame | `0 32px 64px rgba(0,0,0,0.18)` | Elevación del frame de teléfono |
| OTP box activo | `0 0 0 3px var(--olive)` | Foco visible en campo OTP activo |
| Card hover (index) | `0 4px 16px rgba(0,0,0,0.06)` | Hover sutil en tarjetas del índice |
| Desktop frame | `0 24px 48px rgba(0,0,0,0.12)` | Elevación del frame de desktop |

---

## Iconografía

Los iconos se implementan como SVG inline con las siguientes propiedades base:

| Propiedad | Valor |
|-----------|-------|
| Viewbox | `0 0 24 24` |
| Fill | `none` |
| Stroke | `currentColor` |
| Stroke-width | `2` (nav, formularios) / `2.5` (botones back, OTP) |
| Tamaño mobile | `18px` (lista), `16px` (nav admin) |
| Tamaño desktop | `16px` (sidebar), `14px` (tabla) |

**Excepción:** El icono de Telegram usa `fill="currentColor"` en lugar de stroke, ya que es una forma sólida.

---

## Tokens de z-index

| Capa | Valor | Uso |
|------|-------|-----|
| Chrome (barra de navegación del mockup) | `100` | Siempre encima del contenido |
| Tab bar mobile | `10` | Sobre el scroll de contenido |
| Sticky footer (resdet-footer) | Auto | Pegado al fondo del frame del teléfono |
