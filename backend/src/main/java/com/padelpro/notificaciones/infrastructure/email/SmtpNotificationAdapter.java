package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SMTP adapter for the {@link NotificationPort} (change {@code usuarios-alta-edicion-email}, D1/D4/D5).
 *
 * <p>Sends transactional emails through a {@link JavaMailSender} configured by environment
 * ({@code spring.mail.*} ← {@code MAIL_HOST/PORT/USERNAME/PASSWORD}). The sender address comes from
 * {@code MAIL_FROM}.
 *
 * <p><b>Asynchronous &amp; fault-tolerant (D4):</b> the send runs on a separate thread ({@code @Async})
 * and any delivery failure is caught and logged — it never propagates to the caller, so account
 * activation/creation always completes. The temporary password is never written to the logs
 * (RN-RGPD-04): only the recipient and the flow (with/without password) are logged.
 */
@Component
public class SmtpNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationAdapter(JavaMailSender mailSender,
                                   @Value("${app.mail.from:${spring.mail.username:no-reply@padelpro.local}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void sendWelcomeEmail(WelcomeEmail email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email.recipientEmail());
            message.setSubject(WelcomeEmailTemplate.subject());
            message.setText(WelcomeEmailTemplate.body(email));

            mailSender.send(message);

            // RN-RGPD-04: log the recipient and the flow, never the password itself.
            log.info("Welcome email sent to {} (withPassword={})",
                    email.recipientEmail(), email.hasPassword());
        } catch (Exception ex) {
            // D4: a delivery failure must not break activation/creation. Swallow and log.
            // Never include the message body / password in the log — only a safe summary.
            log.warn("Failed to send welcome email to {} (withPassword={}): {}",
                    email.recipientEmail(), email.hasPassword(), ex.getClass().getSimpleName());
        }
    }

    /**
     * Send a transactional event email synchronously (change {@code notificaciones-eventos-email}).
     *
     * <p>Unlike {@link #sendWelcomeEmail}, delivery failures are <b>not</b> swallowed here: the
     * exception propagates so {@code EmailNotificationService} can mark the {@code notification_log}
     * entry {@code FAILED} and the retry job can pick it up. RN-RGPD-04: only the recipient is logged,
     * never the body (which carries no secrets by construction, but stays out of the logs regardless).
     */
    @Override
    public void sendEmail(EmailMessage email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.recipient());
        message.setSubject(email.subject());
        message.setText(email.body());

        mailSender.send(message);

        // RN-RGPD-04: do not log the recipient email in clear — mask the local part.
        log.info("Notification email sent to {} (subject='{}')", maskEmail(email.recipient()),
                email.subject());
    }

    /**
     * Mask an email for logging (RN-RGPD-04): keep the first two characters of the local part and the
     * domain, e.g. {@code ana@example.com → an***@example.com}. Never logs the address in clear.
     */
    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }
}
