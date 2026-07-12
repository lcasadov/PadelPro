package com.padelpro.notificaciones.domain.model;

/**
 * Channel of a {@link NotificationLog} entry.
 *
 * <p>{@code EMAIL} is delivered by the SMTP adapter (change {@code notificaciones-eventos-email}).
 * {@code TELEGRAM_DIRECT} and {@code TELEGRAM_GROUP} are delivered by the Telegram adapter (change
 * {@code notificaciones-telegram}): a direct message to the recipient's linked chat (RN-TEL-03) and a
 * broadcast to the club's configured group (Req 6) respectively.
 */
public enum NotificationType {
    EMAIL,
    TELEGRAM_DIRECT,
    TELEGRAM_GROUP
}
