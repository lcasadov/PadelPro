package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.EmailMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the payment-receipt email (change {@code notificaciones-eventos-email}, Req 5 / D4),
 * following the plain-text pattern of {@code WelcomeEmailTemplate}.
 *
 * <p>Carries only the payment facts required for a receipt: amount, date and a payment reference
 * (transaction id / order id). <b>Never</b> any card data, token or secret (RN-RGPD-04 / RN-PAY-03).
 */
public final class PaymentReceiptEmailTemplate {

    private static final String SUBJECT = "Recibo de pago — PadelPro";
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PaymentReceiptEmailTemplate() {
    }

    public static EmailMessage build(String recipientEmail, String recipientName, BigDecimal amount,
                                     OffsetDateTime paidAt, String reference) {
        String greeting = (recipientName != null && !recipientName.isBlank())
                ? "Hola " + recipientName + "," : "Hola,";
        StringBuilder body = new StringBuilder(greeting).append("\n\n")
                .append("Hemos recibido tu pago. Este es tu recibo:\n\n");
        if (amount != null) {
            body.append("    Importe: ").append(amount.toPlainString()).append(" €\n");
        }
        if (paidAt != null) {
            body.append("    Fecha: ").append(DATE_TIME.format(paidAt)).append('\n');
        }
        if (reference != null && !reference.isBlank()) {
            body.append("    Referencia: ").append(reference).append('\n');
        }
        body.append("\nGracias por tu reserva.\n\nUn saludo,\nEl equipo de PadelPro");
        return new EmailMessage(recipientEmail, SUBJECT, body.toString());
    }
}
