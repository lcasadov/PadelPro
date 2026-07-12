package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.application.service.TelegramAuditRecorder;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.usuarios.application.dto.TelegramLinkInstructionsResponse;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Telegram link/unlink use cases behind {@code PATCH /api/usuarios/me} (auth-otp-telegram,
 * spec Requirement 1 & 3).
 *
 * <ul>
 *   <li><b>LINK</b>: generate a {@code TELEGRAM_LINK} OTP (RN-TEL-02) and return the instruction to
 *       send {@code /vincular <code>} to the bot. The webhook completes the link.</li>
 *   <li><b>UNLINK</b>: clear {@code telegram_chat_id}/{@code telegram_linked_at}, revoke the user's
 *       active OTPs and audit {@code TELEGRAM_UNLINKED}.</li>
 * </ul>
 */
@Service
public class TelegramLinkService {

    private final OtpService otpService;
    private final UserRepositoryPort userRepositoryPort;
    private final TelegramAuditRecorder auditRecorder;

    public TelegramLinkService(OtpService otpService,
                               UserRepositoryPort userRepositoryPort,
                               TelegramAuditRecorder auditRecorder) {
        this.otpService = otpService;
        this.userRepositoryPort = userRepositoryPort;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Start Telegram linking: generate the OTP and return the instructions to complete it via the
     * bot. The user must be authenticated (id from the JWT).
     */
    @Transactional
    public TelegramLinkInstructionsResponse initiateLink(Long userId) {
        requireUser(userId);
        GeneratedOtp otp = otpService.generate(userId, OtpType.TELEGRAM_LINK);
        String instructions = "Abre Telegram y envía /vincular " + otp.code()
                + " al bot @PadelProBot. El código caduca en 10 minutos.";
        return new TelegramLinkInstructionsResponse(instructions, otp.code(), otp.expiresAt());
    }

    /** Unlink Telegram: clear the link, revoke active OTPs and audit the action. */
    @Transactional
    public UserProfileResponse unlink(Long userId) {
        User user = requireUser(userId);
        user.setTelegramChatId(null);
        user.setTelegramLinkedAt(null);
        User saved = userRepositoryPort.save(user);
        otpService.revokeAllActive(userId);
        auditRecorder.record(TelegramAuditActions.TELEGRAM_UNLINKED, userId, "channel=WEB");
        return UserProfileService.toProfileResponse(saved);
    }

    private User requireUser(Long userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
