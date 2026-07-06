package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.EmailMessage;

/**
 * Builds the reservation-cancellation email (change {@code notificaciones-eventos-email}, Req 4 /
 * D4), following the plain-text pattern of {@code WelcomeEmailTemplate}.
 *
 * <p>Includes the cancellation reason only when available. No sensitive data (RN-RGPD-04). Sent to
 * the reservation owner only (v1 does not notify non-owner participants).
 */
public final class ReservationCancelledEmailTemplate {

    private static final String SUBJECT = "Reserva cancelada — PadelPro";

    private ReservationCancelledEmailTemplate() {
    }

    public static EmailMessage build(String recipientEmail, String recipientName, String reason) {
        String greeting = (recipientName != null && !recipientName.isBlank())
                ? "Hola " + recipientName + "," : "Hola,";
        StringBuilder body = new StringBuilder(greeting).append("\n\n")
                .append("Tu reserva en PadelPro ha sido cancelada.\n");
        if (reason != null && !reason.isBlank()) {
            body.append("\nMotivo: ").append(reason).append('\n');
        }
        body.append("\nSi tienes cualquier duda, contacta con el club.\n\n")
                .append("Un saludo,\nEl equipo de PadelPro");
        return new EmailMessage(recipientEmail, SUBJECT, body.toString());
    }
}
