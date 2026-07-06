package com.padelpro.notificaciones.domain.model;

/**
 * Lifecycle of a {@link NotificationLog} entry (change {@code notificaciones-eventos-email}, D2).
 *
 * <ul>
 *   <li>{@code PENDING} — the entry has been recorded but delivery has not completed yet.</li>
 *   <li>{@code SENT} — the notification was delivered successfully.</li>
 *   <li>{@code FAILED} — the last delivery attempt failed; the retry job may pick it up while
 *       {@code attempts < 3} (Req 3).</li>
 * </ul>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
