package com.padelpro.notificaciones.domain.model;

/**
 * Plain-text email to be delivered through the {@code notificaciones} outbound port (change
 * {@code notificaciones-eventos-email}).
 *
 * <p>The three fields map 1:1 to a {@link NotificationLog} entry's {@code recipient}, {@code subject}
 * and {@code message}. <b>RN-RGPD-04:</b> {@code body} and {@code recipient} must never carry
 * passwords, tokens, OTPs or card data — only reservation/payment facts.
 *
 * @param recipient destination email address
 * @param subject   email subject line
 * @param body      plain-text body (provider-agnostic, mirroring {@code WelcomeEmailTemplate})
 */
public record EmailMessage(String recipient, String subject, String body) {
}
