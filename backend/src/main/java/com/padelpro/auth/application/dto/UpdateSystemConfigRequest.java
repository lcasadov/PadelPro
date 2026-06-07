package com.padelpro.auth.application.dto;

import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;

public record UpdateSystemConfigRequest(
        String clubName,
        String clubDescription,
        PistaState pistaState,
        PaymentGateway paymentGateway,
        String redsysMerchantId,
        String redsysMerchantKey,
        String telegramBotToken,
        Integer maxParticipantsPerPista
) {
}
