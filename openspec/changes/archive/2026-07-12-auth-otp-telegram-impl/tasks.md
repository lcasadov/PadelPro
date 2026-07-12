## 1. Persistencia (Flyway)

- [x] 1.1 Migración `V15__otp_codes_and_telegram_link.sql`: tabla `otp_codes` (`id`, `user_id` FK, `code_hash`, `type`, `attempts` int default 0, `used` bool default false, `expires_at`, `created_at`) + índices por `user_id`/`type` y por `code_hash`/`type`
- [x] 1.2 En la misma migración: unicidad de `users.telegram_chat_id` (índice único parcial `WHERE NOT NULL`; columnas ya existían desde V4) + `telegram_webhook_secret` cifrado en `system_config`

## 2. Dominio + aplicación OTP

- [x] 2.1 Entidad/persistencia `OtpCode` + repositorio (`OtpCodeRepository` implementa `OtpCodeRepositoryPort`)
- [x] 2.2 `OtpService`: generar (6 díg., TTL 10min, SHA-256, invalida OTP previo del mismo tipo), verificar (no expirado/no usado/<3 intentos; incrementa `attempts`; invalida al 3er fallo), todo RN-AUTH-07; nunca loggear el código en claro (RN-RGPD-04)
- [x] 2.3 Eventos de auditoría para generación/verificación/invalidación (`OtpAuditRecorder` → `audit_log`, sin código en claro)

## 3. Endpoint verificar OTP

- [x] 3.1 `POST /api/otp/verificar` (JWT USER/ADMIN): body `{otpCode, type}`; 200 ok (marca used), 422 fallo (contrato `{error,message,timestamp}` con código `OTP_INVALID|OTP_EXPIRED|OTP_MAX_ATTEMPTS`)
- [x] 3.2 `SecurityConfig`: `/api/otp/**` → `authenticated()`

## 4. Vinculación / desvinculación Telegram

- [x] 4.1 `PATCH /api/usuarios/me` (`telegramAction=LINK|UNLINK`): LINK → genera OTP `TELEGRAM_LINK` + devuelve instrucciones; UNLINK → limpia `telegram_chat_id`/`telegram_linked_at` + invalida OTPs + audita `TELEGRAM_UNLINKED` (`TelegramLinkService`)
- [x] 4.2 Guardas: no permitir vincular si el `chat_id` ya pertenece a otra cuenta (guard en el webhook). NOTA: la respuesta LINK **sí** incluye el código en las instrucciones (necesario para completar el flujo y coherente con el escenario del spec); ver desvío reportado abajo

## 5. Webhook Telegram

- [x] 5.1 `POST /api/bot/telegram` (`permitAll` en SecurityConfig): valida `X-Telegram-Bot-Api-Secret-Token` (comparación constant-time) contra `system_config`; 403 + audita `TELEGRAM_WEBHOOK_INVALID_SECRET` si falta/incorrecto (RN-TEL-01)
- [x] 5.2 Parseo del update: comando `/vincular XXXXXX` → verifica OTP `TELEGRAM_LINK`, fija `telegram_chat_id`+`telegram_linked_at`, marca OTP used, audita `TELEGRAM_LINKED`, responde por el bot
- [x] 5.3 Casos: OTP expirado (mensaje "expirado"), chat ya vinculado (rechazo sin modificar), cuenta no vinculada manda comando (mensaje "Vincula primero")

## 6. Port de mensajería Telegram

- [x] 6.1 `TelegramPort.enviarMensaje(chatId, texto)` (puerto de salida)
- [x] 6.2 Adapter real (`TelegramApiAdapter`): HTTP `sendMessage` a la Bot API con `telegram_bot_token` descifrado de `system_config`; degrada a no-op sin token (RN-TEL-03); nunca rompe el flujo (excepción tragada)
- [x] 6.3 Config `system_config`: claves `telegram_bot_token` (ya existía) y `telegram_webhook_secret` (columna nueva; cifrado AES-256-GCM en `SystemConfigService.updateConfig`, D-OTP-01)

## 7. Frontend (mockups 13–15)

- [x] 7.1 Mi perfil: estado de vinculación + acciones vincular/desvincular
- [x] 7.2 Pantalla de instrucciones del bot tras iniciar la vinculación
- [x] 7.3 Pantalla/flujo de OTP: contador TTL en monospace, aviso de invalidación tras 3 fallos, sin mostrar el código

## 8. Tests y cobertura

- [x] 8.1 Unit backend: `OtpService` (generación, TTL, SHA-256, intentos, invalidación, reemplazo, revoke) + `OtpCode` + endpoint `OtpController` (200/422/400)
- [x] 8.2 Backend webhook: validación de secret (403 unit + HTTP), comando `/vincular` ok/expirado/duplicado/no-vinculado, auditoría (`TelegramWebhookServiceTest` + `TelegramWebhookControllerTest`)
- [x] 8.3 Adapter Telegram: servidor HTTP mockeado (`MockRestServiceServer` sobre `RestClient`; MockWebServer no está en el classpath) + no-op sin token + fallo tragado
- [x] 8.4 Frontend: tests de las pantallas (vitest + MSW)
- [x] 8.5 Cobertura del código nuevo ≥80% a nivel unitario (39 tests backend nuevos verdes; gate global JaCoCo/IT corre en CI)

## 9. QA y cierre

- [x] 9.1 Backend `mvn -DskipITs test`: suite unitaria completa en verde (39 tests nuevos + resto sin regresión). Gate JaCoCo/IT corre en CI.
- [x] 9.2 Frontend `tsc -b` + `lint` + `test` (coverage) + `build` en verde
- [x] 9.3 Contrato front↔back reconciliado (vinculación por webhook, `telegramLinked` en perfil); `openspec/plan.md` actualizado (Wave 2B ✅); PR

## Notas de implementación
- **Flujo de vinculación (corregido tras reconciliar contrato):** la app muestra el `otpCode`; el usuario envía `/vincular <código>` al bot; el **webhook** fija `telegram_chat_id`; la app confirma re-consultando `GET /api/usuarios/me` (`telegramLinked`). `POST /api/otp/verificar` NO vincula (solo verifica; queda para operaciones críticas futuras).
- **OtpTelegramPage eliminada:** sin consumidor real (confirmar/cancelar reserva vía bot está fuera de alcance); se reintroducirá con esa feature.
- **Contrato:** `PATCH /api/usuarios/me {telegramAction:LINK|UNLINK}`; LINK→`{instructions,otpCode,expiresAt}`. `system_config`: `telegram_bot_token` + `telegram_webhook_secret` (cifrados AES-256-GCM). Token real lo aporta el operador al desplegar; tests con stub/no-op.
