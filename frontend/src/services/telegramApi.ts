// auth-otp-telegram — cliente de vinculación / desvinculación de Telegram.
//
// Contrato REAL del backend (auth-otp-telegram-impl, spec Requirement 1):
//   - PATCH /api/usuarios/me { telegramAction: 'LINK' }
//       → TelegramLinkInstructionsResponse { instructions, otpCode, expiresAt }
//       El backend SÍ devuelve el `otpCode` (6 dígitos): es el código que el
//       usuario debe VER para enviarlo al bot como `/vincular <otpCode>`. La
//       vinculación se completa en el WEBHOOK del bot (TelegramWebhookService),
//       no por un endpoint de verificación. RN-RGPD-04 se cumple porque el
//       código nunca se loggea ni se persiste en claro (solo su SHA-256).
//   - PATCH /api/usuarios/me { telegramAction: 'UNLINK' } → limpia la vinculación.
//   - GET /api/usuarios/me (UserProfileResponse) expone `telegramLinked` (=
//       telegram_chat_id != null): fuente de verdad para confirmar la vinculación.
//
// Todas las peticiones usan la instancia axios base (`api`) para heredar el
// interceptor de refresh de sesión (auth-session-refresh).
import { api } from './httpClient';
import type { UsuarioMe } from './usuariosApi';

/** Acción de vinculación enviada en el PATCH del perfil. */
export type TelegramAction = 'LINK' | 'UNLINK';

/**
 * Respuesta al iniciar la vinculación (mockup 15). Los campos son opcionales por
 * robustez frente a variaciones del backend; la UI aplica fallbacks seguros.
 */
export interface TelegramLinkInitiateResponse {
  /** Texto de instrucciones legible (p. ej. "Envía /vincular 123456 al bot"). */
  instructions?: string;
  /** Código OTP de 6 dígitos que el usuario debe enviar al bot. */
  otpCode?: string;
  /** Instante ISO-8601 de caducidad del código (TTL 10 min, RN-AUTH-07). */
  expiresAt?: string;
}

/**
 * Inicia la vinculación de Telegram. El servidor genera el OTP `TELEGRAM_LINK` y
 * lo devuelve para que el usuario lo envíe al bot.
 */
export async function iniciarVinculacionTelegramApi(
  token: string
): Promise<TelegramLinkInitiateResponse> {
  const { data } = await api.patch<TelegramLinkInitiateResponse>(
    '/usuarios/me',
    { telegramAction: 'LINK' satisfies TelegramAction },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return data ?? {};
}

/**
 * Desvincula la cuenta de Telegram (limpia `telegram_chat_id`). El backend
 * invalida además los OTP activos del usuario (Requirement 3 del spec).
 */
export async function desvincularTelegramApi(token: string): Promise<void> {
  await api.patch(
    '/usuarios/me',
    { telegramAction: 'UNLINK' satisfies TelegramAction },
    { headers: { Authorization: `Bearer ${token}` } }
  );
}

/** Re-exporta el tipo de perfil para consumidores del flujo Telegram. */
export type { UsuarioMe };
