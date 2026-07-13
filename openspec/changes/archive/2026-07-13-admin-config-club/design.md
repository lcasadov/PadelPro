## Context

La capability `configuracion-club` ya tiene backend completo: entidad singleton `SystemConfig` (id=1), `GET`/`PATCH /api/admin/sistema/config` (ADMIN, `@PreAuthorize`), cifrado AES-256-GCM de secretos y enmascarado en respuesta. El frontend, en cambio, solo tiene páginas admin para Usuarios, Dashboard y Cobros — no hay pantalla de configuración. El objetivo es cerrar ese hueco de UI sin tocar el contrato.

Patrón de referencia en el frontend: `AdminPagosPage` / `AdminUsuariosPage` (ruta protegida por rol, servicio en `src/services/*Api.ts` sobre el `httpClient` compartido con interceptor de refresh, token en memoria vía `AuthContext`).

## Goals

- El ADMIN puede leer y editar la configuración global desde `/admin/config`.
- Precio/hora y estado de pista editables (peticiones explícitas del usuario).
- Credenciales de Telegram editables desde la UI (desbloquea el setup del bot).
- Cero regresiones de backend; el enmascarado de secretos y la semántica PATCH parcial se respetan tal cual.

## Decisions

- **D1 — Solo frontend.** No se añade código de backend. El endpoint ya cubre todos los campos; si en verificación aparece algún gap (p.ej. un campo no mapeado), se corrige en el propio servicio/DTO existente, no se crea nada nuevo.
- **D2 — Secretos enmascarados, edición opt-in.** Los campos de secreto (bot token, webhook secret, claves Redsys) se muestran con placeholder `••••` y `value` vacío. Si el admin deja el campo vacío, NO se envía en el PATCH → el backend conserva el valor. Solo se envía cuando el admin teclea uno nuevo. Esto evita sobrescribir un secreto con el enmascarado.
- **D3 — PATCH parcial desde el form.** El servicio construye el body solo con los campos modificados (o con los no-secretos siempre + secretos solo si tienen valor). Coherente con la semántica PATCH del backend (Requisito 2 de `configuracion-club`).
- **D4 — Validación en cliente espejo del backend.** `pricePerHour > 0`, `maxParticipants ≥ 1`, `cancellationDeadlineHours ≥ 0`. El backend sigue siendo la autoridad (400 `VALIDATION_ERROR`); el cliente solo mejora UX.
- **D5 — Ruta protegida por rol.** `/admin/config` se envuelve con el guard de rol ADMIN existente (mismo que `/admin/usuarios`). Un USER que navegue ahí es redirigido/denegado en cliente, y el backend responde 403 de todos modos.
- **D6 — Estado de pista.** Selector ACTIVA/MANTENIMIENTO. Se avisa en la UI de que MANTENIMIENTO oculta toda la disponibilidad al jugador (RN-RES-04), para que el admin entienda el efecto.

## Risks

- **Sobrescritura accidental de un secreto** si el form enviara el valor enmascarado. Mitigado por D2 (no enviar secretos vacíos).
- **Desalineación del DTO front↔back**: el `PagoHistorial`/config podría devolver nombres distintos a los esperados. Mitigado leyendo el `SystemConfigResponse` real en verificación y mapeando explícitamente (mismo patrón que se hizo en `adminPagosApi`).
- **Falsa sensación de "Telegram listo"**: configurar el token en la UI no registra el webhook en Telegram (eso es `setWebhook`, fuera de la app). Se documenta en el runbook/ayuda de la pantalla.

## Migration Plan

Sin migraciones de datos ni de esquema. Cambio puramente aditivo en el frontend. Rollback = revertir el PR (la página desaparece; el backend sigue igual).
