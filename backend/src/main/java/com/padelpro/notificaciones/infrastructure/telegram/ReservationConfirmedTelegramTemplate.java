package com.padelpro.notificaciones.infrastructure.telegram;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Plain-text Telegram messages for a confirmed reservation (change {@code notificaciones-telegram},
 * Req 1 / Req 6 / D4).
 *
 * <ul>
 *   <li>{@link #direct} — personal notification to the owner's linked chat (RN-TEL-03): includes the
 *       reservation facts (date, time, duration, amount).</li>
 *   <li>{@link #group} — broadcast to the club group (Req 6): only the slot facts (date, time,
 *       duration), NO personal data (RN-RGPD), so other players can see a court is booked.</li>
 * </ul>
 */
public final class ReservationConfirmedTelegramTemplate {

    private ReservationConfirmedTelegramTemplate() {
    }

    /** Direct message to the reservation owner. */
    public static String direct(String recipientName, LocalDate date, LocalTime startTime,
                                Integer durationMinutes, BigDecimal amount) {
        StringBuilder body = new StringBuilder(TelegramTextFormat.greeting(recipientName))
                .append("\n\n")
                .append("✅ Tu reserva en PadelPro está confirmada.\n");
        appendSlot(body, date, startTime, durationMinutes);
        if (amount != null) {
            body.append("Importe: ").append(TelegramTextFormat.amount(amount)).append(" €\n");
        }
        body.append("\n¡Nos vemos en la pista!");
        return body.toString();
    }

    /** Broadcast to the club group — no personal data, only the booked slot. */
    public static String group(LocalDate date, LocalTime startTime, Integer durationMinutes) {
        StringBuilder body = new StringBuilder("🎾 Nueva reserva confirmada.\n");
        appendSlot(body, date, startTime, durationMinutes);
        return body.toString();
    }

    private static void appendSlot(StringBuilder body, LocalDate date, LocalTime startTime,
                                   Integer durationMinutes) {
        if (date != null) {
            body.append("Fecha: ").append(TelegramTextFormat.DATE.format(date)).append('\n');
        }
        if (startTime != null) {
            body.append("Hora: ").append(TelegramTextFormat.TIME.format(startTime)).append('\n');
        }
        if (durationMinutes != null) {
            body.append("Duración: ").append(durationMinutes).append(" minutos\n");
        }
    }
}
