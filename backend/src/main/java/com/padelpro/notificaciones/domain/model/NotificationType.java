package com.padelpro.notificaciones.domain.model;

/**
 * Channel of a {@link NotificationLog} entry (change {@code notificaciones-eventos-email}).
 *
 * <p>Only {@code EMAIL} is implemented in this change; the Telegram channel is deferred to the
 * {@code auth-otp-telegram} capability (see proposal — Fuera de alcance).
 */
public enum NotificationType {
    EMAIL
}
