package com.padelpro.auth.application.dto;

import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;

import java.time.OffsetDateTime;

public record SystemConfigResponse(
        String clubName,
        String clubDescription,
        PistaState pistaState,
        PaymentGateway paymentGateway,
        Integer maxParticipantsPerPista,
        Boolean telegramBotConfigured,
        Boolean redsysConfigured,
        OffsetDateTime updatedAt
) {
}
