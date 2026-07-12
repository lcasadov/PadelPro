package com.padelpro.mensajeria.domain.audit;

/**
 * Audit action constants for the Telegram messaging side of auth-otp-telegram (RN-TEL-01).
 */
public final class TelegramAuditActions {

    public static final String TELEGRAM_LINKED                 = "TELEGRAM_LINKED";
    public static final String TELEGRAM_UNLINKED               = "TELEGRAM_UNLINKED";
    public static final String TELEGRAM_WEBHOOK_INVALID_SECRET = "TELEGRAM_WEBHOOK_INVALID_SECRET";

    private TelegramAuditActions() {
    }
}
