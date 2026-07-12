package com.padelpro.mensajeria.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.mensajeria.domain.exception.TelegramWebhookForbiddenException;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.application.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes Telegram Bot webhook updates (auth-otp-telegram, spec Requirement 1 & 4, D-OTP-04).
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate {@code X-Telegram-Bot-Api-Secret-Token} against the configured secret (RN-TEL-01);
 *       a missing/incorrect secret is audited and rejected with 403 <em>before</em> any parsing.</li>
 *   <li>Parse the update and dispatch the {@code /vincular XXXXXX} command: resolve the
 *       {@code TELEGRAM_LINK} OTP, guard against a chat already linked to another account, commit the
 *       link, mark the OTP used and confirm via the bot.</li>
 *   <li>Any other message from an unlinked chat gets the "link first" reply; no business op runs.</li>
 * </ol>
 *
 * <p>The clear OTP is never logged (RN-RGPD-04).
 */
public class TelegramWebhookService {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookService.class);
    private static final Pattern VINCULAR = Pattern.compile("^/vincular(?:@\\w+)?\\s+(\\d{6})\\b");

    private final TelegramConfigService configService;
    private final OtpService otpService;
    private final UserRepositoryPort userRepositoryPort;
    private final TelegramAuditRecorder auditRecorder;
    private final TelegramPort telegramPort;
    private final ObjectMapper objectMapper;
    private final String profileUrl;

    public TelegramWebhookService(TelegramConfigService configService,
                                  OtpService otpService,
                                  UserRepositoryPort userRepositoryPort,
                                  TelegramAuditRecorder auditRecorder,
                                  TelegramPort telegramPort,
                                  ObjectMapper objectMapper,
                                  String profileUrl) {
        this.configService = configService;
        this.otpService = otpService;
        this.userRepositoryPort = userRepositoryPort;
        this.auditRecorder = auditRecorder;
        this.telegramPort = telegramPort;
        this.objectMapper = objectMapper;
        this.profileUrl = profileUrl;
    }

    /**
     * Validate the secret header and process the raw update body.
     *
     * @throws TelegramWebhookForbiddenException (→ 403) when the secret is missing/incorrect
     */
    @Transactional
    public void handleUpdate(String secretHeader, String rawBody) {
        validateSecret(secretHeader);
        parseAndDispatch(rawBody);
    }

    // -------------------------------------------------------------------------
    // Secret validation (RN-TEL-01)
    // -------------------------------------------------------------------------

    private void validateSecret(String secretHeader) {
        Optional<String> expected = configService.getWebhookSecret();
        if (secretHeader == null || expected.isEmpty() || !constantTimeEquals(secretHeader, expected.get())) {
            auditRecorder.record(TelegramAuditActions.TELEGRAM_WEBHOOK_INVALID_SECRET, null,
                    "reason=" + (secretHeader == null ? "missing_header"
                            : expected.isEmpty() ? "no_secret_configured" : "mismatch"));
            throw new TelegramWebhookForbiddenException();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Update parsing + command dispatch
    // -------------------------------------------------------------------------

    private void parseAndDispatch(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }
        JsonNode message;
        try {
            message = objectMapper.readTree(rawBody).path("message");
        } catch (Exception e) {
            log.warn("Telegram webhook: unparseable update body ({})", e.getClass().getSimpleName());
            return;
        }
        String chatId = message.path("chat").path("id").asText(null);
        String text = message.path("text").asText("");
        if (chatId == null || chatId.isBlank()) {
            return;
        }

        Matcher matcher = VINCULAR.matcher(text.trim());
        if (matcher.find()) {
            handleVincular(chatId, matcher.group(1));
        } else {
            handleOtherMessage(chatId);
        }
    }

    private void handleVincular(String chatId, String code) {
        OtpCode otp;
        try {
            otp = otpService.resolveActiveLinkOtp(code);
        } catch (OtpVerificationException ex) {
            if ("OTP_EXPIRED".equals(ex.getCode())) {
                telegramPort.enviarMensaje(chatId, "El código ha expirado. Solicita uno nuevo desde tu perfil.");
            } else {
                telegramPort.enviarMensaje(chatId, "El código es inválido. Revisa el código o solicita uno nuevo desde tu perfil.");
            }
            return;
        }

        // Guard: the chat must not already belong to a different account (spec Req 1, scenario 3).
        Optional<User> chatOwner = userRepositoryPort.findByTelegramChatId(chatId);
        if (chatOwner.isPresent() && !chatOwner.get().getId().equals(otp.getUserId())) {
            telegramPort.enviarMensaje(chatId,
                    "Este Telegram ya está vinculado a otra cuenta. Contacta con el administrador.");
            return;
        }

        User user = userRepositoryPort.findById(otp.getUserId()).orElse(null);
        if (user == null) {
            telegramPort.enviarMensaje(chatId, "No se ha podido completar la vinculación. Inténtalo de nuevo.");
            return;
        }

        user.setTelegramChatId(chatId);
        user.setTelegramLinkedAt(OffsetDateTime.now());
        userRepositoryPort.save(user);
        otpService.consume(otp);

        auditRecorder.record(TelegramAuditActions.TELEGRAM_LINKED, user.getId(), "channel=TELEGRAM");
        telegramPort.enviarMensaje(chatId, "Tu cuenta de PadelPro ha quedado vinculada correctamente.");
    }

    private void handleOtherMessage(String chatId) {
        Optional<User> linked = userRepositoryPort.findByTelegramChatId(chatId);
        if (linked.isEmpty()) {
            telegramPort.enviarMensaje(chatId, "Vincula primero tu cuenta en " + profileUrl);
        }
        // A linked account sending a non-command message triggers no business operation in this scope.
    }
}
