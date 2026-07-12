package com.padelpro.mensajeria.domain.exception;

/**
 * Raised when a Telegram webhook request is missing or presents an incorrect
 * {@code X-Telegram-Bot-Api-Secret-Token} (RN-TEL-01). Mapped to HTTP 403 by the global handler.
 */
public class TelegramWebhookForbiddenException extends RuntimeException {

    public TelegramWebhookForbiddenException() {
        super("Invalid or missing Telegram webhook secret token");
    }
}
