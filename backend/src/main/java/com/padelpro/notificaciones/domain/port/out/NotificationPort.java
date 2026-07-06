package com.padelpro.notificaciones.domain.port.out;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;

/**
 * Outbound domain port for sending transactional notifications (change
 * {@code usuarios-alta-edicion-email}, D1).
 *
 * <p>Business code (e.g. {@code UserAdminService}) depends on this interface, never on JavaMail or a
 * concrete SMTP client — the adapter lives in {@code notificaciones.infrastructure} (hexagonal /
 * ports &amp; adapters).
 *
 * <p><b>Fault tolerance (D4):</b> implementations MUST NOT propagate a delivery failure to the
 * caller — account activation/creation must complete even when SMTP is unavailable. A failure is
 * logged (without ever exposing a password, RN-RGPD-04) and swallowed. Delivery is asynchronous.
 */
public interface NotificationPort {

    /**
     * Send the welcome email triggered when an account becomes ACTIVE.
     *
     * <p>Never throws on delivery failure: the send is best-effort and asynchronous.
     *
     * @param email the welcome email content (with or without a temporary password, per the flow)
     */
    void sendWelcomeEmail(WelcomeEmail email);

    /**
     * Deliver a transactional event email (reservation confirmed/cancelled, payment receipt — change
     * {@code notificaciones-eventos-email}).
     *
     * <p><b>Contract differs from {@link #sendWelcomeEmail}:</b> this method is <em>synchronous</em>
     * and <em>propagates</em> a delivery failure so the caller ({@code EmailNotificationService}) can
     * record the outcome ({@code SENT} vs {@code FAILED}) in {@code notification_log} and let the retry
     * job re-attempt it. The asynchrony and fault-tolerance (RN-NOT-01/02 — a failure never blocks nor
     * reverts the business transaction) live one level up, in the {@code @Async} notification service.
     *
     * @param email the message to send (no sensitive data in subject/body — RN-RGPD-04)
     * @throws org.springframework.mail.MailException if delivery fails
     */
    void sendEmail(EmailMessage email);
}
