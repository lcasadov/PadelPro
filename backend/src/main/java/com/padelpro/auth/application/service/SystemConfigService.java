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

        if (request.telegramBotToken() != null && !request.telegramBotToken().isBlank()) {
            config.setTelegramBotToken(encryptionService.encrypt(request.telegramBotToken()));
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
    }
}
