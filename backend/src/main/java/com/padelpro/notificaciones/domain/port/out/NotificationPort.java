package com.padelpro.notificaciones.domain.port.out;

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
}
