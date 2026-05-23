# Capability: `exportaciones-rgpd`

## Resumen
Implementación de los derechos del titular de datos conforme al Reglamento General de
Protección de Datos (RGPD): derecho al olvido (Art. 17, anonimización), portabilidad
(Art. 20, exportación JSON), y acceso (Art. 15, perfil propio). Esta capability es crítica
para el cumplimiento legal; cualquier fallo en su implementación expone al club a sanciones
de la AEPD. La anonimización es irreversible y no elimina registros contables ni de auditoría.

## Fase
🟢 Fase 1

## Reglas de negocio implicadas
- **RN-RGPD-01**: La eliminación de cuenta de usuario se implementa como anonimización (no borrado físico): `first_name='ANONIMIZADO'`, `last_name='ANONIMIZADO'`, `email='anonimized-{id}@padelpro.local'`, `phone=NULL`, `telegram_chat_id=NULL`, `telegram_linked_at=NULL`; genera entrada `audit_log` con `action='USER_ANONYMIZED'`.
- **RN-RGPD-02**: Los datos financieros (`payments`, `reservations`) se conservan un mínimo de 5 años por obligación fiscal (AEAT); la anonimización no elimina ni modifica estas tablas, aunque la FK `registered_by_id` en `payments` pasa a NULL por el SET NULL de la FK.
- **RN-RGPD-03**: Las respuestas de error de los endpoints de exportaciones-rgpd no exponen datos personales de otros usuarios.
- **RN-RGPD-04**: Los logs del proceso de anonimización y exportación no contienen los datos personales procesados.
- **RN-RGPD-05**: El derecho al olvido no elimina registros de `audit_log` (son inmutables por diseño legal); la FK `user_id` pasa a NULL (SET NULL) tras la anonimización.
- **RN-RGPD-06**: El usuario anonimizado no puede autenticarse porque su `email` ya no es válido y su `login` permanece (aunque el `password_hash` podría anularse opcionalmente para impedir acceso); se recomienda además marcar `status=INACTIVE`.
- **RN-RGPD-07**: Los `refresh_tokens` activos del usuario anonimizado se revocan (`revoked=true`) y los `otp_codes` activos se invalidan (`used=true`) como parte del proceso de anonimización.

## Entidades implicadas
- **`users`**: entidad principal afectada por la anonimización. Campos anonimizados: `first_name`, `last_name`, `email`, `phone`, `telegram_chat_id`, `telegram_linked_at`. Campos preservados: `id`, `login`, `registered_at`, `status` (→ INACTIVE).
- **`participants`**: `external_name='ANONIMIZADO'` y `external_phone=NULL` para los registros vinculados al usuario anonimizado (cuando `user_id = {id}`).
- **`refresh_tokens`**: `revoked=true` para todos los tokens del usuario.
- **`otp_codes`**: `used=true` para todos los OTP activos del usuario.
- **`payments`**: NO se modifican. `registered_by_id` pasa a NULL por FK SET NULL si el usuario era admin que registró pagos en efectivo. Los pagos vinculados a sus reservas permanecen intactos.
- **`reservations`**: NO se modifican. Las reservas históricas del usuario permanecen con `owner_id` intacto (FK RESTRICT, el usuario anonimizado sigue existiendo en la tabla).
- **`audit_log`**: NO se modifica. La FK `user_id` pasa a NULL (SET NULL) si había entradas previas; se genera una nueva entrada `USER_ANONYMIZED`.

## Endpoints
**Anonimización (derecho al olvido — Art. 17):**
- `DELETE /api/admin/usuarios/{id}` — Requerido ROLE_ADMIN. En v1.0 este endpoint implementa la anonimización (soft-delete: `status=INACTIVE` + anonimización de campos personales). Ver `docs/openapi.yaml` operationId `desactivarUsuario`.

**Acceso al perfil propio (Art. 15, parcial):**
- `GET /api/usuarios/me` — Requerido ROLE_USER o ROLE_ADMIN. Devuelve los datos del propio usuario autenticado. Ver `docs/openapi.yaml` operationId `getMiPerfil`.

**Portabilidad (Art. 20) — Decisión abierta D-SEC-03:**
- No existe endpoint de exportación en v1.0. Proceso manual: el ADMIN exporta los datos del usuario mediante consulta directa a la BD. En Fase 2 se implementará `GET /api/usuarios/me/exportar` que devuelva un JSON con todos los datos del titular.

> **Nota:** La decisión D-SEC-03 de `docs/security-design.md` tiene pendiente de confirmación
> si `GET /api/usuarios/me/exportar` es requisito para el lanzamiento de v1.0.

## Permisos
| Operación | ADMIN | USER | No autenticado |
|---|---|---|---|
| Anonimizar cuenta propia (Art. 17) | ✅ (sobre sí mismo) | ✅ (solicitud manual; Admin ejecuta) | ❌ |
| Anonimizar cuenta ajena (Art. 17) | ✅ | ❌ | ❌ |
| Consultar perfil propio (Art. 15) | ✅ | ✅ | ❌ |
| Exportar datos propios JSON (Art. 20, Fase 2) | ✅ | ✅ | ❌ |
| Exportar datos de otro usuario | ✅ | ❌ | ❌ |

## Requirements

### Requirement 1: Derecho al olvido — anonimización irreversible de datos personales
**El sistema DEBE, cuando se invoca `DELETE /api/admin/usuarios/{id}`, sobrescribir los campos de datos personales del usuario con valores neutros, marcar la cuenta como `INACTIVE`, revocar todos los tokens activos e invalidar todos los OTP activos, en una única transacción atómica.**

#### Scenario 1: ADMIN anonimiza una cuenta — datos personales sobrescritos
- **GIVEN** un usuario con `id=42`, `status=ACTIVE`, `email='jugador@example.com'`, `telegram_chat_id='987654321'`
- **WHEN** el ADMIN invoca `DELETE /api/admin/usuarios/42`
- **THEN** en una única transacción: `users.first_name='ANONIMIZADO'`, `users.last_name='ANONIMIZADO'`, `users.email='anonimized-42@padelpro.local'`, `users.phone=NULL`, `users.telegram_chat_id=NULL`, `users.telegram_linked_at=NULL`, `users.status='INACTIVE'`, AND todos los `refresh_tokens` con `user_id=42` pasan a `revoked=true`, AND todos los `otp_codes` activos con `user_id=42` pasan a `used=true`, AND se inserta en `audit_log` con `action='USER_ANONYMIZED'`, `user_id`= id del ADMIN ejecutor, `entity_type='USER'`, `entity_id='42'`, AND la respuesta HTTP es 204 No Content

#### Scenario 2: Usuario anonimizado no puede autenticarse
- **GIVEN** un usuario previamente anonimizado con `status=INACTIVE` y `email='anonimized-42@padelpro.local'`
- **WHEN** se intenta `POST /api/auth/login` con las credenciales originales del usuario
- **THEN** el sistema devuelve 403 Forbidden (estado INACTIVE), Y se genera entrada `audit_log` con `action='USER_LOGIN_FAILED'`, Y no se revelan los datos actuales del usuario en la respuesta de error (RN-RGPD-03)

#### Scenario 3: ADMIN no puede anonimizarse a sí mismo
- **GIVEN** un ADMIN autenticado con `id=1`
- **WHEN** el ADMIN invoca `DELETE /api/admin/usuarios/1` (su propio id)
- **THEN** el sistema devuelve 422 Unprocessable Entity con código de error `SELF_DEACTIVATION_FORBIDDEN`, Y ningún dato del ADMIN es modificado en BD (RN-AUTH-05)

### Requirement 2: Datos financieros preservados tras anonimización
**El sistema DEBE conservar todos los registros de `payments` y `reservations` vinculados al usuario anonimizado, sin modificar su contenido, para cumplir la obligación de retención fiscal de 5 años (RN-RGPD-02).**

#### Scenario 4: Pagos del usuario anonimizado permanecen intactos
- **GIVEN** un usuario con id=42 que tiene 3 reservas con pagos en estado `PAID`
- **WHEN** el ADMIN anonimiza la cuenta del usuario (id=42)
- **THEN** los 3 registros en `payments` vinculados a esas reservas permanecen en BD sin modificación de `amount`, `status`, `paid_at`, `redsys_order_id` ni `transaction_id`, AND las reservas con `owner_id=42` permanecen en BD con todos sus datos (la FK RESTRICT previene el borrado físico del usuario), AND los `participants` con `user_id=42` mantienen el `user_id` (FK SET NULL no aplica aquí — el usuario sigue existiendo como entidad con datos anonimizados)

#### Scenario 5: `audit_log` no se modifica tras anonimización — FK pasa a NULL
- **GIVEN** un usuario con id=42 que tiene 15 entradas en `audit_log` con `user_id=42`
- **WHEN** el ADMIN anonimiza la cuenta del usuario
- **THEN** las 15 entradas de `audit_log` permanecen intactas en sus campos `action`, `entity_type`, `entity_id`, `details`, `ip_address`, `channel` y `created_at`, AND el campo `user_id` de esas 15 filas pasa a NULL (FK SET NULL), AND se genera UNA NUEVA fila en `audit_log` con `action='USER_ANONYMIZED'`

### Requirement 3: Portabilidad de datos — exportación en JSON (Art. 20)
**El sistema DEBE, cuando se invoque el endpoint de exportación (Fase 2), devolver un JSON estructurado con todos los datos personales del titular: perfil, historial de reservas y pagos, sin incluir datos de terceros.**

#### Scenario 6: Usuario exporta sus propios datos (Fase 2)
- **GIVEN** un usuario autenticado con `status=ACTIVE` que tiene 5 reservas y 3 pagos
- **WHEN** el usuario invoca `GET /api/usuarios/me/exportar` (Fase 2)
- **THEN** la respuesta es un JSON con: `perfil` (campos no sensibles del usuario: login, email, phone, firstName, lastName, registeredAt), `reservas` (lista de reservas donde es `owner_id`: fecha, hora, estado, participantes propios sin datos de terceros), `pagos` (lista de pagos propios: importe, estado, fecha), Y el JSON no contiene `password_hash`, tokens, OTP codes ni datos de tarjeta

### Requirement 4: Acceso a datos propios — perfil completo (Art. 15)
**El sistema DEBE devolver al usuario autenticado todos sus datos personales almacenados cuando invoque `GET /api/usuarios/me`, sin exponer datos de otros usuarios.**

#### Scenario 7: Usuario accede a su propio perfil
- **GIVEN** un usuario autenticado con `status=ACTIVE`
- **WHEN** el usuario invoca `GET /api/usuarios/me`
- **THEN** la respuesta incluye los campos del `UsuarioResponse`: `id`, `login`, `email`, `phone`, `firstName`, `lastName`, `role`, `status`, `telegramLinked`, `telegramLinkedAt`, `registeredAt`, Y no incluye `password_hash` ni ningún campo sensible

#### Scenario 8: ADMIN anonimiza cuenta con `telegram_chat_id` vinculado — campo nullificado
- **GIVEN** un usuario con `telegram_chat_id='987654321'` vinculado
- **WHEN** el ADMIN ejecuta la anonimización sobre esa cuenta
- **THEN** `users.telegram_chat_id=NULL` y `users.telegram_linked_at=NULL` en BD, AND si el bot de Telegram intentara identificar al usuario por ese `chat_id` en el futuro, la búsqueda `SELECT ... WHERE telegram_chat_id='987654321'` devuelve 0 resultados, AND el usuario ya no puede recibir notificaciones ni comandos del bot vía ese chat_id

## Casos límite
- Si el usuario a anonimizar ya está en `status=INACTIVE` (desactivado previamente sin anonimizar), la operación de anonimización debe ejecutarse igualmente para sobrescribir los campos personales.
- Si cualquier paso de la transacción de anonimización falla (ej. error en `UPDATE users`), toda la transacción hace rollback y los datos del usuario permanecen sin cambios; se debe generar un log de error de aplicación sin incluir datos del usuario.
- El `login` del usuario anonimizado NO se modifica para preservar la unicidad de la restricción y evitar colisiones; el `login` de un usuario anonimizado queda "congelado" y no puede ser reutilizado por otro usuario.
- La anonimización de `participants.external_name` solo aplica cuando `participants.user_id = {id_usuario_anonimizado}`, es decir, para los registros de participación del propio usuario, no para los jugadores externos (`user_id IS NULL`).
- En Fase 2, el endpoint de exportación debe responder con `Content-Type: application/json` y `Content-Disposition: attachment; filename="mis-datos-padelpro-{fecha}.json"` para facilitar la descarga.
- El plazo legal de respuesta al derecho al olvido (Art. 17) es 30 días; PadelPro debe poder ejecutar la anonimización en ese plazo desde la solicitud del titular.

## Dependencias con otras capabilities
- **`autenticacion`**: el proceso de anonimización revoca `refresh_tokens` e invalida `otp_codes`; también impide futuros logins del usuario anonimizado.
- **`auditoria`**: la anonimización genera la entrada `USER_ANONYMIZED` en `audit_log` (Requirement 1, Scenario 1), que es inmutable y permanece aunque el usuario sea anonimizado.
- **`notificaciones`**: tras la anonimización, el usuario no puede recibir notificaciones porque `email` y `telegram_chat_id` han sido nullificados; `MensajeriaPort` debe manejar esta condición sin lanzar excepción.
- **`reservas`**: las reservas históricas del usuario permanecen intactas con `owner_id` apuntando al usuario anonimizado (que sigue existiendo en la tabla).
- **`pagos-redsys`**: los pagos históricos permanecen intactos; `registered_by_id` pasa a NULL por FK SET NULL si el usuario anonimizado era el admin que registró pagos en efectivo.

## Mockups asociados

Los siguientes mockups en alta fidelidad ilustran la experiencia de usuario para esta capability. La fuente única de verdad UX es [`docs/ux/README.md`](../../../docs/ux/README.md).

### Pantallas

| # | Pantalla | Dispositivo | Permisos | Mockup |
|---|----------|-------------|----------|--------|
| 19 | Mis datos y privacidad | Mobile | USER | [`23-mis-datos.html`](../../../docs/ux/mockups/23-mis-datos.html) |
| 20 | Confirmar eliminación | Mobile | USER | [`24-eliminar-cuenta.html`](../../../docs/ux/mockups/24-eliminar-cuenta.html) |

### Flujos relacionados

Esta capability participa en los siguientes flujos (ver [`docs/ux/flujos.md`](../../../docs/ux/flujos.md)):

- **Flujo RGPD — derecho al olvido** — cubre el flujo completo: desde la pantalla de mis datos y privacidad (pantalla 19) con referencias a los artículos RGPD, hasta la pantalla de confirmación de eliminación de cuenta (pantalla 20) con la advertencia de irreversibilidad.

### Notas de UX

> - La pantalla de confirmar eliminación (pantalla 20) debe requerir que el usuario escriba literalmente "ELIMINAR" (o su nombre) para confirmar la acción destructiva; el botón permanece deshabilitado hasta que el campo de confirmación coincide.
> - La anonimización es irreversible; la pantalla 20 debe listar explícitamente las consecuencias (pérdida de acceso, datos anonimizados, historial preservado por obligación fiscal) antes de permitir continuar (RN-RGPD-01, RN-RGPD-02).
> - La pantalla de mis datos (pantalla 19) debe mostrar referencia al artículo legal aplicable (Art. 15, 16, 17 RGPD) para cada acción disponible.
