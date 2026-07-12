package com.padelpro.notificaciones.infrastructure.telegram;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared formatting helpers for the plain-text Telegram templates (change
 * {@code notificaciones-telegram}, D4). Kept separate from the SMTP HTML/plain-text email templates
 * ({@code notificaciones.infrastructure.email}) so the two channels never share formatting concerns.
 *
 * <p>All Telegram bodies are short plain text and carry only reservation/payment facts — never
 * passwords, tokens, OTPs or card data (RN-RGPD-04).
 */
final class TelegramTextFormat {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private TelegramTextFormat() {
    }

    /** Formats an amount with the Spanish decimal comma and exactly 2 decimals (e.g. {@code 22,50}). */
    static String amount(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(ES);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    /** Personalised greeting, or a neutral one when the name is missing. */
    static String greeting(String name) {
        return (name != null && !name.isBlank()) ? "Hola " + name + "," : "Hola,";
    }
}
