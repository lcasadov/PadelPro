package com.padelpro.mensajeria.domain.port.out;

/**
 * Outcome of a best-effort Telegram send (auth-otp-telegram / notificaciones-telegram). Returned by
 * {@link TelegramPort#enviarMensaje} instead of {@code void} so callers can audit the real result of
 * the delivery (RN-NOT-01, spec Req 2 scenarios 3/4) without the port ever throwing.
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@link Outcome#SENT} — the Bot API accepted the message.</li>
 *   <li>{@link Outcome#FAILED} — a real delivery failure (bot blocked, timeout, non-2xx). {@link #error}
 *       carries a sanitized reason (never a secret — RN-RGPD-04).</li>
 *   <li>{@link Outcome#SKIPPED} — no attempt was made because the bot is not configured (no token).
 *       This is a clean degradation (RN-TEL-03), NOT a real failure, so it must not be audited as one.</li>
 * </ul>
 *
 * @param outcome the delivery outcome
 * @param error   a sanitized reason for {@code FAILED}/{@code SKIPPED}, or {@code null} for {@code SENT}
 */
public record TelegramSendResult(Outcome outcome, String error) {

    public enum Outcome {
        SENT,
        FAILED,
        SKIPPED
    }

    /** A successful delivery. */
    public static TelegramSendResult sent() {
        return new TelegramSendResult(Outcome.SENT, null);
    }

    /** A real delivery failure with a sanitized reason (no secrets — RN-RGPD-04). */
    public static TelegramSendResult failed(String error) {
        return new TelegramSendResult(Outcome.FAILED, error);
    }

    /** No attempt made because the bot is not configured (RN-TEL-03) — not a real failure. */
    public static TelegramSendResult skipped(String reason) {
        return new TelegramSendResult(Outcome.SKIPPED, reason);
    }

    public boolean isSent() {
        return outcome == Outcome.SENT;
    }

    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    public boolean isSkipped() {
        return outcome == Outcome.SKIPPED;
    }
}
