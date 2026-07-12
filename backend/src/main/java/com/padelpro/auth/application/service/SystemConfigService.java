package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.SystemConfigResponse;
import com.padelpro.auth.application.dto.UpdateSystemConfigRequest;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;

/**
 * Manages centralized system configuration (singleton system_config table).
 * D-CONF-06: Injection pattern — other services inject this directly.
 * D-CONF-07: Split nomenclature with SystemSecretsService (private).
 * D-CONF-08: Service layer handles all decryption (never in controller).
 */
public class SystemConfigService {

    private final SystemConfigRepositoryPort repositoryPort;
    private final EncryptionService encryptionService;

    public SystemConfigService(SystemConfigRepositoryPort repositoryPort,
                               EncryptionService encryptionService) {
        this.repositoryPort = repositoryPort;
        this.encryptionService = encryptionService;
    }

    /**
     * Get configuration without exposing secrets (D-CONF-04).
     * Secrets are masked as true/false flags.
     */
    public SystemConfigResponse getConfig() {
        SystemConfig config = repositoryPort.findById(1L)
                .orElseThrow(() -> new RuntimeException("System configuration not found"));

        boolean telegramConfigured = config.getTelegramBotToken() != null && !config.getTelegramBotToken().isBlank();
        boolean redsysConfigured = config.getRedsysMerchantKey() != null && !config.getRedsysMerchantKey().isBlank();

        return new SystemConfigResponse(
                config.getClubName(),
                config.getClubDescription(),
                config.getPistaState(),
                config.getPaymentGateway(),
                config.getMaxParticipantsPerPista(),
                config.getPricePerHour(),
                config.getCancellationDeadlineHours(),
                telegramConfigured,
                redsysConfigured,
                config.getUpdatedAt()
        );
    }

    /**
     * Update configuration with validation (D-CONF-05).
     * Encrypts secrets before saving (D-CONF-02).
     */
    public SystemConfigResponse updateConfig(UpdateSystemConfigRequest request) {
        validateUpdateRequest(request);

        SystemConfig config = repositoryPort.findById(1L)
                .orElseThrow(() -> new RuntimeException("System configuration not found"));

        config.setClubName(request.clubName());
        config.setClubDescription(request.clubDescription());
        config.setPistaState(request.pistaState());
        config.setPaymentGateway(request.paymentGateway());
        config.setMaxParticipantsPerPista(request.maxParticipantsPerPista());
        // reservas (US-007): pricing fields are optional on PATCH — when omitted (null), keep the
        // current stored value instead of nulling a NOT NULL column.
        if (request.pricePerHour() != null) {
            config.setPricePerHour(request.pricePerHour());
        }
        if (request.cancellationDeadlineHours() != null) {
            config.setCancellationDeadlineHours(request.cancellationDeadlineHours());
        }

        if (request.telegramBotToken() != null && !request.telegramBotToken().isBlank()) {
            config.setTelegramBotToken(encryptionService.encrypt(request.telegramBotToken()));
        }

        // auth-otp-telegram (D-OTP-01): encrypt the Telegram webhook secret like the other club secrets.
        if (request.telegramWebhookSecret() != null && !request.telegramWebhookSecret().isBlank()) {
            config.setTelegramWebhookSecret(encryptionService.encrypt(request.telegramWebhookSecret()));
        }

        if (request.redsysMerchantId() != null && !request.redsysMerchantId().isBlank()) {
            config.setRedsysMerchantId(encryptionService.encrypt(request.redsysMerchantId()));
        }

        if (request.redsysMerchantKey() != null && !request.redsysMerchantKey().isBlank()) {
            config.setRedsysMerchantKey(encryptionService.encrypt(request.redsysMerchantKey()));
        }

        config = repositoryPort.save(config);

        boolean telegramConfigured = config.getTelegramBotToken() != null && !config.getTelegramBotToken().isBlank();
        boolean redsysConfigured = config.getRedsysMerchantKey() != null && !config.getRedsysMerchantKey().isBlank();

        return new SystemConfigResponse(
                config.getClubName(),
                config.getClubDescription(),
                config.getPistaState(),
                config.getPaymentGateway(),
                config.getMaxParticipantsPerPista(),
                config.getPricePerHour(),
                config.getCancellationDeadlineHours(),
                telegramConfigured,
                redsysConfigured,
                config.getUpdatedAt()
        );
    }

    /**
     * D-CONF-05: Validation rules.
     * REDSYS requires both merchant_id and merchant_key.
     */
    private void validateUpdateRequest(UpdateSystemConfigRequest request) {
        if (request.paymentGateway() == PaymentGateway.REDSYS) {
            if (request.redsysMerchantId() == null || request.redsysMerchantId().isBlank()) {
                throw new ValidationException("REDSYS payment_gateway requires redsys_merchant_id");
            }
            if (request.redsysMerchantKey() == null || request.redsysMerchantKey().isBlank()) {
                throw new ValidationException("REDSYS payment_gateway requires redsys_merchant_key");
            }
        }

        if (request.maxParticipantsPerPista() == null || request.maxParticipantsPerPista() <= 0) {
            throw new ValidationException("maxParticipantsPerPista must be greater than 0");
        }

        // reservas (US-007): pricing + cancellation policy are optional on PATCH, but when supplied
        // must stay within the DB CHECK bounds (V8: price_per_hour > 0, deadline >= 0).
        if (request.pricePerHour() != null
                && request.pricePerHour().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ValidationException("pricePerHour must be greater than 0");
        }
        if (request.cancellationDeadlineHours() != null && request.cancellationDeadlineHours() < 0) {
            throw new ValidationException("cancellationDeadlineHours must be zero or greater");
        }
    }
}
