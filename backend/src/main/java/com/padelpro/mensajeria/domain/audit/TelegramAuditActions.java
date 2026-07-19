package com.padelpro.mensajeria.domain.audit;

/**
 * Audit action constants for the Telegram messaging side of auth-otp-telegram (RN-TEL-01).
 */
public final class TelegramAuditActions {

    public static final String TELEGRAM_LINKED                 = "TELEGRAM_LINKED";
    public static final String TELEGRAM_UNLINKED               = "TELEGRAM_UNLINKED";
    public static final String TELEGRAM_WEBHOOK_INVALID_SECRET = "TELEGRAM_WEBHOOK_INVALID_SECRET";

    // bot-telegram-reservas — reservation commands issued through the bot dispatcher.
    public static final String TELEGRAM_RESERVA_CREATED        = "TELEGRAM_RESERVA_CREATED";
    public static final String TELEGRAM_RESERVA_CONFIRMED      = "TELEGRAM_RESERVA_CONFIRMED";
    public static final String TELEGRAM_RESERVA_CANCELLED      = "TELEGRAM_RESERVA_CANCELLED";
    public static final String TELEGRAM_COMMAND_REJECTED       = "TELEGRAM_COMMAND_REJECTED";

    private TelegramAuditActions() {
    }
}
