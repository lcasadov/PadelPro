package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.application.service.TelegramAuditRecorder;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.usuarios.application.dto.TelegramLinkInstructionsResponse;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramLinkService — link/unlink use cases (spec Req 1 & 3)")
class TelegramLinkServiceTest {

    @Mock private OtpService otpService;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private TelegramAuditRecorder auditRecorder;

    private TelegramLinkService service() {
        return new TelegramLinkService(otpService, userRepositoryPort, auditRecorder);
    }

    private static User user(String chatId) {
        User u = new User("login", "hash", "First", "Last", "u@x.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        u.setTelegramChatId(chatId);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 42L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    @Test
    @DisplayName("initiateLink generates a TELEGRAM_LINK OTP and returns instructions with the code")
    void initiate_link() {
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(user(null)));
        OffsetDateTime exp = OffsetDateTime.now().plusMinutes(10);
        when(otpService.generate(42L, OtpType.TELEGRAM_LINK)).thenReturn(new GeneratedOtp("123456", exp));

        TelegramLinkInstructionsResponse res = service().initiateLink(42L);

        assertThat(res.otpCode()).isEqualTo("123456");
        assertThat(res.instructions()).contains("123456").contains("/vincular");
        assertThat(res.expiresAt()).isEqualTo(exp);
    }

    @Test
    @DisplayName("initiateLink throws when the user does not exist")
    void initiate_link_user_missing() {
        when(userRepositoryPort.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().initiateLink(7L)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("unlink clears the link, revokes OTPs and audits TELEGRAM_UNLINKED")
    void unlink() {
        User u = user("999");
        when(userRepositoryPort.findById(42L)).thenReturn(Optional.of(u));
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse res = service().unlink(42L);

        assertThat(u.getTelegramChatId()).isNull();
        assertThat(u.getTelegramLinkedAt()).isNull();
        assertThat(res.telegramLinked()).isFalse();
        verify(otpService).revokeAllActive(42L);
        verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_UNLINKED), eq(42L), any());
    }
}
