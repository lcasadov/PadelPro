package com.padelpro.notificaciones.infrastructure.telegram;

/**
 * Plain-text Telegram message for a cancelled reservation (change {@code notificaciones-telegram},
 * Req 4 / D4). Direct message to the owner, including the cancellation reason only when present.
 */
public final class ReservationCancelledTelegramTemplate {

    private ReservationCancelledTelegramTemplate() {
    }

    public static String direct(String recipientName, String reason) {
        StringBuilder body = new StringBuilder(TelegramTextFormat.greeting(recipientName))
                .append("\n\n")
                .append("❌ Tu reserva en PadelPro ha sido cancelada.\n");
        if (reason != null && !reason.isBlank()) {
            body.append("Motivo: ").append(reason).append('\n');
        }
        return body.toString();
    }
}
