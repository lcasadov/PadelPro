## 1. Setup y andamiaje

- [x] 1.1 Crear GitHub Issue para el change `usuarios` (label `type:user-story`, Milestone `EP-01 Acceso e Identidad`, Sprint 1)
- [x] 1.2 Crear rama `feat/usuarios` desde `develop`
- [x] 1.3 Crear estructura de paquetes Maven hexagonal: `com.padelpro.usuarios.domain`, `com.padelpro.usuarios.application`, `com.padelpro.usuarios.infrastructure` (compilable, vacío)

## 2. Backend — Dominio (DTOs, puertos, excepciones)

- [x] 2.1 Crear `UserProfileResponse` (campos: id, login, firstName, lastName, email, phone, status, role, telegramLinked)
- [x] 2.2 Crear `UpdateMyProfileCommand` (campos: firstName, lastName, email, phone — sin role ni status)
- [x] 2.3 Crear `UserAdminResponse` (todos los campos del perfil incluyendo role, status, registeredAt, updatedAt)
- [x] 2.4 Crear `CreateUserAdminCommand` (campos: login, firstName, lastName, email, password, phone, role opcional)
- [x] 2.5 Crear `UpdateUserAdminCommand` (todos los campos modificables: firstName, lastName, email, phone, role, status — sin login)
- [x] 2.6 Crear `PagedUsersResponse` (wrapper: content, totalElements, totalPages, page, size)
- [x] 2.7 Crear excepciones de dominio: `UserNotFoundException` (→ 404), `UserNotPendingException` (→ 422), `AdminSelfDeactivationException` (→ 422), `EmailConflictException` (→ 409)
- [x] 2.8 Añadir métodos al `UserRepositoryPort`: `findAll(status, pageable)`, `existsByEmailAndIdNot(email, id)`, `existsByLogin(login)`

## 3. Backend — Servicios de aplicación

- [x] 3.1 Implementar `UserProfileService.getMyProfile(userId)` → devuelve `UserProfileResponse`
- [x] 3.2 Implementar `UserProfileService.updateMyProfile(userId, command)` → valida conflicto de email, actualiza, devuelve `UserProfileResponse`
- [x] 3.3 Implementar `UserAdminService.createUser(command)` → crea con `status=ACTIVE`, hashea BCrypt cost 12, audita `USER_CREATED_BY_ADMIN`
- [x] 3.4 Implementar `UserAdminService.approveUser(targetId)` → verifica `status=PENDING`, transiciona a `ACTIVE`, audita `USER_APPROVED`
- [x] 3.5 Implementar `UserAdminService.listUsers(status, pageable)` → fuerza `size = min(size, 100)`, devuelve `PagedUsersResponse`
- [x] 3.6 Implementar `UserAdminService.getUser(id)` → devuelve `UserAdminResponse` o lanza `UserNotFoundException`
- [x] 3.7 Implementar `UserAdminService.updateUser(id, command)` → actualiza campos, audita `USER_ROLE_CHANGED` si role cambia
- [x] 3.8 Implementar `UserAdminService.deactivateUser(targetId, adminId)` → verifica RN-AUTH-05, cambia a `status=INACTIVE`, audita `USER_DEACTIVATED`

## 4. Backend — Infraestructura (controllers, seguridad, auditoría)

- [x] 4.1 Añadir acciones al enum/constantes `AuditAction`: `USER_CREATED_BY_ADMIN`, `USER_APPROVED`, `USER_DEACTIVATED`, `USER_ROLE_CHANGED`
- [x] 4.2 Implementar `UsuariosMeController`: `GET /api/usuarios/me` (200) y `PATCH /api/usuarios/me` (200/400/409)
- [x] 4.3 Implementar `AdminUsuariosController`: `GET /api/admin/usuarios` (200), `POST /api/admin/usuarios` (201/409), `GET /api/admin/usuarios/{id}` (200/404), `PATCH /api/admin/usuarios/{id}` (200), `PATCH /api/admin/usuarios/{id}/aprobar` (200/422), `DELETE /api/admin/usuarios/{id}` (204/422)
- [x] 4.4 Extender `SecurityFilterChain`: añadir `.requestMatchers("/api/admin/**").hasRole("ADMIN")` y `.requestMatchers("/api/usuarios/me").authenticated()`
- [x] 4.5 Añadir handler en `GlobalExceptionHandler` para `UserNotFoundException` (404), `UserNotPendingException` (422), `AdminSelfDeactivationException` (422), `EmailConflictException` (409)

## 5. Backend — Tests unitarios (TDD — escribir antes de implementar los servicios)

- [x] 5.1 `UserProfileServiceTest`: `should_return_profile_when_user_exists` (R-1)
- [x] 5.2 `UserProfileServiceTest`: `should_update_profile_when_fields_are_valid` (R-1)
- [x] 5.3 `UserProfileServiceTest`: `should_throw_email_conflict_when_email_already_taken` (R-1)
- [x] 5.4 `UserProfileServiceTest`: `should_ignore_role_and_status_fields_in_update_command` (R-1)
- [x] 5.5 `UserAdminServiceTest`: `should_create_user_with_active_status_and_bcrypt_hash` (R-2)
- [x] 5.6 `UserAdminServiceTest`: `should_throw_email_conflict_when_creating_duplicate_email` (R-2)
- [x] 5.7 `UserAdminServiceTest`: `should_approve_pending_user_and_audit` (R-2)
- [x] 5.8 `UserAdminServiceTest`: `should_throw_when_approving_non_pending_user` (R-2)
- [x] 5.9 `UserAdminServiceTest`: `should_return_paged_users_with_max_size_100` (R-2)
- [x] 5.10 `UserAdminServiceTest`: `should_throw_not_found_when_user_does_not_exist` (R-2)
- [x] 5.11 `UserAdminServiceTest`: `should_deactivate_user_and_audit` (R-2 + R-3)
- [x] 5.12 `UserAdminServiceTest`: `should_throw_admin_self_deactivation_exception` (R-3)

## 6. Backend — Tests de integración (Testcontainers)

- [x] 6.1 `UsuariosMeControllerIntegrationTest`: `GET /api/usuarios/me` con JWT válido → 200 con campos correctos
- [x] 6.2 `UsuariosMeControllerIntegrationTest`: `GET /api/usuarios/me` sin JWT → 401
- [x] 6.3 `UsuariosMeControllerIntegrationTest`: `PATCH /api/usuarios/me` campos válidos → 200
- [x] 6.4 `UsuariosMeControllerIntegrationTest`: `PATCH /api/usuarios/me` email duplicado → 409
- [x] 6.5 `UsuariosMeControllerIntegrationTest`: `PATCH /api/usuarios/me` con role/status en body → 200 pero campos no cambian
- [x] 6.6 `AdminUsuariosControllerIntegrationTest`: `POST /api/admin/usuarios` válido → 201, `status=ACTIVE`
- [x] 6.7 `AdminUsuariosControllerIntegrationTest`: `POST /api/admin/usuarios` email duplicado → 409
- [x] 6.8 `AdminUsuariosControllerIntegrationTest`: `PATCH /api/admin/usuarios/{id}/aprobar` PENDING → 200, `status=ACTIVE`
- [x] 6.9 `AdminUsuariosControllerIntegrationTest`: `PATCH /api/admin/usuarios/{id}/aprobar` ACTIVE → 422
- [x] 6.10 `AdminUsuariosControllerIntegrationTest`: `GET /api/admin/usuarios?status=PENDING` → 200, solo PENDING
- [x] 6.11 `AdminUsuariosControllerIntegrationTest`: `DELETE /api/admin/usuarios/{id}` → 204, `status=INACTIVE`
- [x] 6.12 `AdminUsuariosControllerIntegrationTest`: `DELETE /api/admin/usuarios/{self}` → 422 (RN-AUTH-05)
- [x] 6.13 `AdminUsuariosControllerIntegrationTest`: `GET /api/admin/usuarios` con `role=USER` → 403
- [x] 6.14 `AdminUsuariosControllerIntegrationTest`: `GET /api/admin/usuarios/{id}` inexistente → 404
- [x] 6.15 ArchUnit test: `com.padelpro.usuarios.domain` no importa nada de `com.padelpro.usuarios.infrastructure`

## 7. Frontend — Mi Perfil

- [x] 7.1 Crear `MiPerfilPage.tsx` con ruta `/perfil` protegida por `PrivateRoute`
- [x] 7.2 Implementar fetch `GET /api/usuarios/me` al montar el componente; mostrar datos del perfil
- [x] 7.3 Implementar formulario de edición con campos firstName, lastName, email, phone (sin role ni status)
- [x] 7.4 Implementar submit `PATCH /api/usuarios/me`; manejar 200 (éxito), 400 (validación), 409 (email en conflicto)
- [x] 7.5 Añadir enlace / botón de acceso a Mi Perfil desde la pantalla principal (post-login)

## 8. Frontend — Tests (Vitest + React Testing Library + MSW)

- [x] 8.1 `MiPerfilPage.test.tsx`: renderiza datos del perfil cargados desde mock `GET /api/usuarios/me`
- [x] 8.2 `MiPerfilPage.test.tsx`: actualización exitosa muestra mensaje de confirmación
- [x] 8.3 `MiPerfilPage.test.tsx`: error 409 muestra mensaje de email en conflicto
- [x] 8.4 `MiPerfilPage.test.tsx`: sin JWT redirige a `/login` (PrivateRoute guard)
