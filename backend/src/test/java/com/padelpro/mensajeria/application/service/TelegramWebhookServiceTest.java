package com.padelpro.mensajeria.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.mensajeria.domain.exception.TelegramWebhookForbiddenException;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.application.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramWebhookService — webhook validation + /vincular (RN-TEL-01, spec Req 1)")
class TelegramWebhookServiceTest {

    @Mock private TelegramConfigService configService;
    @Mock private OtpService otpService;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private TelegramAuditRecorder auditRecorder;
    @Mock private TelegramPort telegramPort;

    private TelegramWebhookService service;

    @BeforeEach
    void setUp() {
        service = new TelegramWebhookService(configService, otpService, userRepositoryPort,
                auditRecorder, telegramPort, new ObjectMapper(), "http://host/perfil");
    }

    private static User userWithId(long id, String chatId) {
        User user = new User("login" + id, "hash", "First", "Last", "u" + id + "@x.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        user.setTelegramChatId(chatId);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private static OtpCode linkOtp(long userId) {
        return new OtpCode(userId, "hash", OtpType.TELEGRAM_LINK,
                OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now());
    }

    private static String update(String chatId, String text) {
        return "{\"message\":{\"chat\":{\"id\":" + chatId + "},\"text\":\"" + text + "\"}}";
    }

    // -------------------------------------------------------------------------
    // Secret validation (RN-TEL-01)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("missing secret header → 403 + audits TELEGRAM_WEBHOOK_INVALID_SECRET")
    void missing_secret_forbidden() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));

        assertThatThrownBy(() -> service.handleUpdate(null, update("999", "/vincular 123456")))
                .isInstanceOf(TelegramWebhookForbiddenException.class);
        verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_WEBHOOK_INVALID_SECRET), isNull(), any());
        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("wrong secret → 403 + audits")
    void wrong_secret_forbidden() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));

        assertThatThrownBy(() -> service.handleUpdate("nope", update("999", "hola")))
                .isInstanceOf(TelegramWebhookForbiddenException.class);
        verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_WEBHOOK_INVALID_SECRET), isNull(), any());
    }

    // -------------------------------------------------------------------------
    // /vincular command (spec Requirement 1)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("valid secret + /vincular OK → links chat, consumes OTP, audits TELEGRAM_LINKED, confirms")
    void vincular_success() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));
        OtpCode otp = linkOtp(42L);
        when(otpService.resolveActiveLinkOtp("123456")).thenReturn(otp);
        when(userRepositoryPort.findByTelegramChatId("999")).thenReturn(Optional.empty());
        User user = userWithId(42L, null);
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(user));

        service.handleUpdate("s3cret", update("999", "/vincular 123456"));

        assertThat(user.getTelegramChatId()).isEqualTo("999");
        assertThat(user.getTelegramLinkedAt()).isNotNull();
        verify(userRepositoryPort).save(user);
        verify(otpService).consume(otp);
        verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_LINKED), eq(42L), any());
        verify(telegramPort).enviarMensaje(eq("999"), contains("vinculada"));
    }

    @Test
    @DisplayName("/vincular with expired OTP → replies expired, no link")
    void vincular_expired() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));
        when(otpService.resolveActiveLinkOtp("123456")).thenThrow(OtpVerificationException.expired());

        service.handleUpdate("s3cret", update("999", "/vincular 123456"));

        verify(telegramPort).enviarMensaje(eq("999"), contains("expirado"));
        verify(userRepositoryPort, never()).save(any());
        verify(otpService, never()).consume(any());
    }

    @Test
    @DisplayName("/vincular when chat already linked to another account → rejects, no modification")
    void vincular_chat_already_linked() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));
        OtpCode otp = linkOtp(42L);
        when(otpService.resolveActiveLinkOtp("123456")).thenReturn(otp);
        when(userRepositoryPort.findByTelegramChatId("999")).thenReturn(Optional.of(userWithId(99L, "999")));

        service.handleUpdate("s3cret", update("999", "/vincular 123456"));

        verify(telegramPort).enviarMensaje(eq("999"), contains("otra cuenta"));
        verify(userRepositoryPort, never()).save(any());
        verify(otpService, never()).consume(any());
    }

    @Test
    @DisplayName("non-command from an unlinked chat → replies 'link first', no business op")
    void unlinked_other_message() {
        when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));
        when(userRepositoryPort.findByTelegramChatId("999")).thenReturn(Optional.empty());

        service.handleUpdate("s3cret", update("999", "hola"));

        verify(telegramPort).enviarMensaje(eq("999"), contains("Vincula primero"));
        verifyNoInteractions(otpService);
    }
}
