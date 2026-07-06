package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.EmailMessage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the reservation-confirmation email (change {@code notificaciones-eventos-email}, Req 1 /
 * D4), following the plain-text pattern of {@code WelcomeEmailTemplate}.
 *
 * <p>Carries only reservation facts (date, time, duration, amount) — no passwords, tokens or card
 * data (RN-RGPD-04).
 */
public final class ReservationConfirmedEmailTemplate {

    private static final String SUBJECT = "Reserva confirmada — PadelPro";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ReservationConfirmedEmailTemplate() {
    }

    public static EmailMessage build(String recipientEmail, String recipientName, LocalDate date,
                                     LocalTime startTime, Integer durationMinutes, BigDecimal amount) {
        String greeting = greeting(recipientName);
        StringBuilder body = new StringBuilder(greeting).append("\n\n")
                .append("Tu reserva en PadelPro ha sido confirmada:\n\n");
        if (date != null) {
            body.append("    Fecha: ").append(DATE.format(date)).append('\n');
        }
        if (startTime != null) {
            body.append("    Hora: ").append(TIME.format(startTime)).append('\n');
        }
        if (durationMinutes != null) {
            body.append("    Duración: ").append(durationMinutes).append(" minutos\n");
        }
        if (amount != null) {
            body.append("    Importe: ").append(amount.toPlainString()).append(" €\n");
        }
        body.append("\n¡Nos vemos en la pista!\n\nUn saludo,\nEl equipo de PadelPro");
        return new EmailMessage(recipientEmail, SUBJECT, body.toString());
    }

    private static String greeting(String name) {
        return (name != null && !name.isBlank()) ? "Hola " + name + "," : "Hola,";
    }
}
