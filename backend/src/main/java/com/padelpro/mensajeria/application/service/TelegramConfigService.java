package com.padelpro.mensajeria.application.service;

import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;

import java.util.Optional;

/**
 * Reads and decrypts the Telegram secrets stored (AES-256-GCM) in {@code system_config}
 * (D-OTP-01): the bot token and the webhook secret. Mirrors {@code RedsysConfigService} — all
 * decryption happens here, never in a controller (D-CONF-08).
 */
public class TelegramConfigService {

    private final SystemConfigRepositoryPort repositoryPort;
    private final EncryptionService encryptionService;

    public TelegramConfigService(SystemConfigRepositoryPort repositoryPort,
                                 EncryptionService encryptionService) {
        this.repositoryPort = repositoryPort;
        this.encryptionService = encryptionService;
    }

    /** Decrypted bot token, or empty when the operator has not configured the bot yet (RN-TEL-03). */
    public Optional<String> getBotToken() {
        return config().map(SystemConfig::getTelegramBotToken).flatMap(this::decrypt);
    }

    /** Decrypted webhook secret, or empty when unset (RN-TEL-01). */
    public Optional<String> getWebhookSecret() {
        return config().map(SystemConfig::getTelegramWebhookSecret).flatMap(this::decrypt);
    }

    private Optional<SystemConfig> config() {
        return repositoryPort.findById(1L);
    }

    private Optional<String> decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(encryptionService.decrypt(encrypted));
    }
}
