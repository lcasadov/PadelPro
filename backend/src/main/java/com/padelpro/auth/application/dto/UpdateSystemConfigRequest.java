package com.padelpro.auth.application.dto;

import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;

import java.math.BigDecimal;

public record UpdateSystemConfigRequest(
        String clubName,
        String clubDescription,
        PistaState pistaState,
        PaymentGateway paymentGateway,
        String redsysMerchantId,
        String redsysMerchantKey,
        String telegramBotToken,
        Integer maxParticipantsPerPista,
        BigDecimal pricePerHour,
        Integer cancellationDeadlineHours,
        // auth-otp-telegram (D-OTP-01): the Telegram webhook secret, stored AES-256-GCM encrypted
        // alongside telegramBotToken. Optional on update — omitting it keeps the stored value.
        String telegramWebhookSecret,
        // notificaciones-telegram (D2): the club's Telegram group chat id, stored in plain text (not a
        // secret). Optional on update — omitting it keeps the stored value.
        String telegramGroupId
) {
    /** Backward-compatible constructor for callers that don't set the Telegram webhook secret. */
    public UpdateSystemConfigRequest(String clubName, String clubDescription, PistaState pistaState,
                                     PaymentGateway paymentGateway, String redsysMerchantId,
                                     String redsysMerchantKey, String telegramBotToken,
                                     Integer maxParticipantsPerPista, BigDecimal pricePerHour,
                                     Integer cancellationDeadlineHours) {
        this(clubName, clubDescription, pistaState, paymentGateway, redsysMerchantId, redsysMerchantKey,
                telegramBotToken, maxParticipantsPerPista, pricePerHour, cancellationDeadlineHours, null);
    }

    /** Backward-compatible constructor for callers that don't set the Telegram group id. */
    public UpdateSystemConfigRequest(String clubName, String clubDescription, PistaState pistaState,
                                     PaymentGateway paymentGateway, String redsysMerchantId,
                                     String redsysMerchantKey, String telegramBotToken,
                                     Integer maxParticipantsPerPista, BigDecimal pricePerHour,
                                     Integer cancellationDeadlineHours, String telegramWebhookSecret) {
        this(clubName, clubDescription, pistaState, paymentGateway, redsysMerchantId, redsysMerchantKey,
                telegramBotToken, maxParticipantsPerPista, pricePerHour, cancellationDeadlineHours,
                telegramWebhookSecret, null);
    }
}
