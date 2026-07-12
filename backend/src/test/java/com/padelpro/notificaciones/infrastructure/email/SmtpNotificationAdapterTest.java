package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.WelcomeEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * TDD unit tests for the SMTP adapter of the {@code notificaciones} capability (D1, D4).
 *
 * <p>No real SMTP server is used — {@link JavaMailSender} is mocked. These tests pin the
 * fault-tolerance contract (a send failure never propagates) and RN-RGPD-04 (the temporary
 * password never reaches the logs).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpNotificationAdapter — welcome email delivery (fault-tolerant, no secrets in logs)")
class SmtpNotificationAdapterTest {

    private static final String FROM = "no-reply@padelpro.test";

    @Mock
    private JavaMailSender mailSender;

    private SmtpNotificationAdapter adapter;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger adapterLogger;

    @BeforeEach
    void setUp() {
        adapter = new SmtpNotificationAdapter(mailSender, FROM);

        adapterLogger = (Logger) LoggerFactory.getLogger(SmtpNotificationAdapter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger.addAppender(logAppender);
        adapterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        adapterLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("sends the welcome email with password via SMTP using the configured sender")
    void sends_welcome_email_with_password() {
        WelcomeEmail email = WelcomeEmail.withPassword("user@example.com", "Ana", "Temp0rary9X");

        adapter.sendWelcomeEmail(email);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getTo()).containsExactly("user@example.com");
        assertThat(sent.getText()).contains("Temp0rary9X");
    }

    @Test
    @DisplayName("sends the approval welcome email without any password")
    void sends_approval_email_without_password() {
        WelcomeEmail email = WelcomeEmail.accountApproved("user@example.com", "Ana");

        adapter.sendWelcomeEmail(email);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
    }

    @Test
    @DisplayName("a SMTP send failure is swallowed — never propagates to the caller (D4)")
    void smtp_failure_does_not_propagate() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        WelcomeEmail email = WelcomeEmail.withPassword("user@example.com", "Ana", "Temp0rary9X");

        assertThatCode(() -> adapter.sendWelcomeEmail(email)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the temporary password never appears in the logs — on success (RN-RGPD-04)")
    void password_not_in_logs_on_success() {
        WelcomeEmail email = WelcomeEmail.withPassword("user@example.com", "Ana", "Temp0rary9X");

        adapter.sendWelcomeEmail(email);

        assertThat(logContent()).doesNotContain("Temp0rary9X");
    }

    @Test
    @DisplayName("the temporary password never appears in the logs — on failure (RN-RGPD-04)")
    void password_not_in_logs_on_failure() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        WelcomeEmail email = WelcomeEmail.withPassword("user@example.com", "Ana", "Temp0rary9X");

        adapter.sendWelcomeEmail(email);

        assertThat(logContent()).doesNotContain("Temp0rary9X");
    }

    // -------------------------------------------------------------------------
    // sendEmail (change notificaciones-eventos-email) — propagates on failure + masks recipient
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendEmail delivers a transactional email with the configured sender + subject/body")
    void send_email_delivers() {
        adapter.sendEmail(new com.padelpro.notificaciones.domain.model.EmailMessage(
                "ana@example.com", "Reserva confirmada", "cuerpo"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getTo()).containsExactly("ana@example.com");
        assertThat(sent.getSubject()).isEqualTo("Reserva confirmada");
        assertThat(sent.getText()).isEqualTo("cuerpo");
        // masked recipient in the log, never in clear (RN-RGPD-04)
        assertThat(logContent()).doesNotContain("ana@example.com").contains("an***@example.com");
    }

    @Test
    @DisplayName("sendEmail propagates a delivery failure (so the notification is marked FAILED)")
    void send_email_propagates_failure() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.sendEmail(
                        new com.padelpro.notificaciones.domain.model.EmailMessage(
                                "ana@example.com", "s", "b")))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("sendEmail masks a short local part (<=2 chars) keeping only the first character")
    void send_email_masks_short_local() {
        adapter.sendEmail(new com.padelpro.notificaciones.domain.model.EmailMessage(
                "an@example.com", "s", "b"));
        assertThat(logContent()).contains("a***@example.com");
    }

    @Test
    @DisplayName("sendEmail masks a recipient with no @ as *** (at <= 0 branch)")
    void send_email_masks_recipient_without_at() {
        adapter.sendEmail(new com.padelpro.notificaciones.domain.model.EmailMessage(
                "no-at-symbol", "s", "b"));
        assertThat(logContent()).contains("***").doesNotContain("no-at-symbol");
    }

    private String logContent() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : logAppender.list) {
            sb.append(e.getFormattedMessage()).append('\n');
            if (e.getThrowableProxy() != null) {
                sb.append(e.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return sb.toString();
    }
}
