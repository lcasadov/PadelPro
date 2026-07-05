# Corrección y mejora de la UI de reservas del jugador (fase-1)

> Orden TDD (Red → Green → Refactor) en cada grupo: primero el/los test(s) que fallan, luego la implementación mínima que los pone en verde. Agente `tester-tdd` para el andamiaje; `test-runner` para ejecutar.
>
> Decisiones de revisión (2026-07-05): buscador de socios (endpoint nuevo) · duraciones 60/90/120 · pago online Redsys → change propio `pagos-redsys` (aquí "pagar ahora" queda deshabilitado).

## 1. Bug de creación de reserva — sesión caducada (D2)

> Causa raíz confirmada en vivo (E2E 2026-07-05): el backend crea la reserva (201); el usuario ve un **401 `AUTH_REQUIRED`** por token caducado que el frontend traduce al mensaje genérico. El refresh silencioso es un change aparte (`auth-session-refresh`, D7); aquí se maneja el 401 localmente.

- [x] 1.1 **[Red]** Test frontend: `POST /api/reservas` → 401 `AUTH_REQUIRED` → la UI muestra "sesión caducada" y redirige a login (hoy cae al genérico "No se pudo completar la reserva")
- [x] 1.2 **[Red]** Test frontend: 400 `VALIDATION_ERROR` con `details` → la UI muestra el detalle; 5xx → mensaje de servidor con reintentar; hoy ambos caen al genérico
- [x] 1.3 **[Green]** `reservasApi.ts`/`messageForError`: mapear 401 `AUTH_REQUIRED` a error de sesión; leer código en `error` y detalle en `details[0]` (no `fieldErrors`); diferenciar 4xx (datos) de 5xx (servidor); genérico solo para 5xx sin código / error de red
- [x] 1.4 **[Green]** `ConfirmarReservaPage`: ante error de sesión, mensaje claro + redirección a login (sin sugerir "reintentar" la misma petición)
- [x] 1.5 **[Green]** Verificar reserva válida con sesión vigente → 201 sin error (test del camino feliz en verde)

## 2. Selector de duración (D3)

- [x] 2.1 **[Red]** Test: elegir 90 min envía `durationMinutes:90`; duración que solaparía la franja no se ofrece; default 60 min
- [x] 2.2 **[Green]** Selector de duración 60/90/120 en confirmación, default 60, enviando `durationMinutes` y reflejándolo en el resumen
- [x] 2.3 **[Green]** Filtrar opciones según disponibilidad de la franja (no solapar / no exceder horario); 409 del backend como red de seguridad

## 3. Compañero registrado vs. externo (D4, D5)

- [x] 3.1 **[Red]** Test backend del endpoint de búsqueda de usuarios: coincidencias devuelven id+nombre; sin coincidencias 200 vacío; sin auth 401; no expone datos sensibles
- [x] 3.2 **[Green]** Endpoint `GET` de búsqueda de usuarios por nombre/email, autenticado, término mínimo, resultados mínimos (id+nombre) y acotados; documentar en `docs/openapi.yaml`
- [x] 3.3 **[Red]** Test frontend: socio → payload con `userId` sin `externalName`; externo → `externalName`(+phone) sin `userId`; participante ambiguo/vacío bloquea el submit (XOR)
- [x] 3.4 **[Green]** Componente de participante con conmutador "socio registrado / invitado externo"; modo socio (buscador de socios → `userId`) y modo externo (nombre + teléfono → `externalName`/`externalPhone`); construir payload XOR

## 4. Navegación a Inicio (spec: navegación)

- [x] 4.1 **[Red]** Test: desde cada página de reservas (Disponibilidad, Confirmar, Mis Reservas, Detalle) el control "Inicio" navega a Home
- [x] 4.2 **[Green]** Añadir el control visible "volver a Inicio" en las 4 páginas

## 5. "Mis Reservas" funcional — sin pago online (D6)

- [x] 5.1 **[Red]** Test: cancelar desde la lista actualiza el estado; estado de pago visible en la tarjeta; "pago diferido" no dispara pago; "pagar ahora" informa indisponibilidad; acciones ocultas según estado
- [x] 5.2 **[Green]** Estado de pago por tarjeta (desde `ReservaResponse.pago`)
- [x] 5.3 **[Green]** Acción "Cancelar" inline (reutiliza `DELETE /api/reservas/{id}`), visible solo si owner y estado cancelable; refrescar lista tras cancelar
- [x] 5.4 **[Green]** Control de método de pago por reserva: "pago en diferido" (sin llamada, cobro presencial) y "pagar ahora" (deshabilitado/informativo "disponible próximamente" hasta Fase B); inhabilitar acciones no aplicables por estado

## 6. QA Fase A

- [x] 6.1 **[Refactor]** Limpieza del delta manteniendo la suite en verde — helpers extraídos (`participanteModel.ts`), sin deuda
- [x] 6.2 `verification-specialist`: PASS — backend IT 34 verdes (contra PG real :5433), frontend 155/155, tsc/eslint limpios. security-auditor: 0 críticos/altos; MEDIO (validar userId participante) corregido; rate-limit del buscador y oráculo email evaluados como aceptables/defensa en profundidad (seguimiento)
- [ ] 6.3 `reality-checker`: journey end-to-end del jugador (buscar → elegir duración → añadir compañero socio y externo → confirmar → ver en Mis Reservas → cancelar → volver a Inicio) — **PENDIENTE de live E2E: requiere desplegar el build de la rama; cubierto a nivel de contrato/unidad por IT backend + tests MSW frontend**
- [x] 6.4 Actualizar `docs/openapi.yaml` (endpoint búsqueda de usuarios; alinear schema del cuerpo de error de reservas con lo que emite el backend) — `ErrorResponse` reescrito a `{ error, message, timestamp, details[]:string }` (el schema viejo `{ code, message, errors[]:FieldError }` no coincidía con `ErrorResponse.java` + `GlobalExceptionHandler`); `FieldError` eliminado (quedaba huérfano).

### Notas — pasada de correcciones de seguridad/hardening (backend, #188)

- **[MEDIO — seguridad] Validar `userId` de participante registrado.** `CrearReservaService.addAdditionalParticipants` ahora valida que un participante con `userId` exista y esté `ACTIVE` vía el nuevo puerto `UserRepositoryPort.existsByIdAndStatus(id, ACTIVE)`; si no, lanza `InvalidReservaStateException("PARTICIPANT_NOT_FOUND", …)` → 422 (rollback, no persiste). La FK `participants.user_id → users(id)` (`fk_part_user`, `ON DELETE SET NULL`) **ya existía** en `V7`; no se añadió migración. Tests IT: userId inexistente → 422, userId no-ACTIVE → 422, userId ACTIVE → 201.
- **[Gap de test] Exclusión de no-ACTIVE en la búsqueda.** Nuevo test en `UsuariosBuscarControllerIntegrationTest`: usuarios PENDING e INACTIVE con nombre coincidente NO aparecen (solo ACTIVE).
- **[BAJO — hardening] `ESCAPE '\'` explícito en el LIKE.** `UserRepository.searchByNameOrEmail` añade `ESCAPE '\\'` a las 4 cláusulas LIKE; test de regresión: término con `%` no vuelca el directorio.

---

> **Pago online Redsys** ("pagar ahora") se aborda en un **change propio `pagos-redsys`** (fase-2), fuera de este change. Aquí la opción queda deshabilitada/informativa (tarea 5.4). Ese change cubrirá: endpoint de iniciar pago (firma HMAC SHA-256, importe congelado, solo owner), webhook idempotente de confirmación, habilitar "pagar ahora" + pantalla de retorno en la UI, y QA de seguridad (`security-auditor`) y end-to-end (`reality-checker`) en el entorno de pruebas Redsys.
