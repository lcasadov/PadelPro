package com.padelpro.notificaciones.infrastructure.telegram;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Plain-text Telegram receipt for a confirmed payment (change {@code notificaciones-telegram},
 * Req 5 / D4). Direct message to the owner with amount, date and a non-sensitive payment reference
 * (never card data — RN-RGPD-04).
 */
public final class PaymentReceiptTelegramTemplate {

    private PaymentReceiptTelegramTemplate() {
    }

    public static String direct(String recipientName, BigDecimal amount, OffsetDateTime paidAt,
                                String reference) {
        StringBuilder body = new StringBuilder(TelegramTextFormat.greeting(recipientName))
                .append("\n\n")
                .append("🧾 Hemos recibido tu pago en PadelPro.\n");
        if (amount != null) {
            body.append("Importe: ").append(TelegramTextFormat.amount(amount)).append(" €\n");
        }
        if (paidAt != null) {
            body.append("Fecha: ").append(TelegramTextFormat.DATE.format(paidAt.toLocalDate()))
                    .append('\n');
        }
        if (reference != null && !reference.isBlank()) {
            body.append("Referencia: ").append(reference).append('\n');
        }
        body.append("\n¡Gracias!");
        return body.toString();
    }
}
