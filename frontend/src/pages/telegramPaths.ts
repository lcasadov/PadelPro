// auth-otp-telegram — rutas y constantes del flujo de vinculación de Telegram.
// La vinculación es de una sola pantalla (mockup 15): se muestra el código OTP
// que el usuario envía al bot y se comprueba el resultado re-consultando el perfil.

export const telegramPaths = {
  /** Pantalla de vinculación de Telegram (mockup 15). */
  vincular: '/perfil/telegram/vincular',
} as const;

/** Username por defecto del bot (sin @) usado para construir el deep link. */
export const DEFAULT_BOT_USERNAME = 'PadelPro_bot';

/** TTL por defecto del OTP en segundos: 10 minutos (RN-AUTH-07), fallback local. */
export const OTP_TTL_SECONDS = 600;

/** Deep-link para abrir el chat del bot en Telegram. */
export function botDeepLink(username: string): string {
  return `https://t.me/${username}`;
}
