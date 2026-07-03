package com.padelpro.notificaciones.infrastructure.email;

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
}
