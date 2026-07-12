package com.padelpro.mensajeria.domain.port.out;

/**
 * Outbound domain port for sending Telegram messages (auth-otp-telegram, D-OTP-05).
 *
 * <p>Business code depends on this interface, never on an HTTP client. Implementations MUST be
 * fault-tolerant (RN-TEL-03): with no bot token configured they degrade to a no-op, and a delivery
 * failure is swallowed — sending a Telegram message never breaks the business flow.
 *
 * <p>The method returns a {@link TelegramSendResult} (never throws) so callers that need to audit the
 * delivery — e.g. {@code notification_log} entries (RN-NOT-01) — can distinguish a real failure from a
 * clean skip. Callers that don't care (e.g. the linking webhook) simply ignore the return value.
 */
public interface TelegramPort {

    /**
     * Best-effort send of {@code texto} to the given Telegram {@code chatId}. Never throws; the outcome
     * (sent / failed / skipped) is returned in the {@link TelegramSendResult}.
     *
     * @param chatId the recipient Telegram chat id
     * @param texto  the message text (no secrets — RN-RGPD-04)
     * @return the delivery outcome (never {@code null})
     */
    TelegramSendResult enviarMensaje(String chatId, String texto);
}
