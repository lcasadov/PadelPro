package com.padelpro.mensajeria.domain.port.out;

/**
 * Outbound domain port for sending Telegram messages (auth-otp-telegram, D-OTP-05).
 *
 * <p>Business code depends on this interface, never on an HTTP client. Implementations MUST be
 * fault-tolerant (RN-TEL-03): with no bot token configured they degrade to a no-op, and a delivery
 * failure is swallowed — sending a Telegram message never breaks the business flow.
 */
public interface TelegramPort {

    /**
     * Best-effort send of {@code texto} to the given Telegram {@code chatId}. Never throws.
     *
     * @param chatId the recipient Telegram chat id
     * @param texto  the message text (no secrets — RN-RGPD-04)
     */
    void enviarMensaje(String chatId, String texto);
}
