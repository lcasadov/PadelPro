package com.padelpro.auth.infrastructure.config;

import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.application.service.SystemConfigService;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for system config service.
 * Instantiates EncryptionService with ENCRYPTION_KEY from environment.
 */
@Configuration
public class SystemConfigConfig {

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Bean
    public EncryptionService encryptionService() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY environment variable is required. Set it in .env or application.yml"
            );
        }
        return new EncryptionService(encryptionKey);
    }

    @Bean
    public SystemConfigService systemConfigService(
            SystemConfigRepositoryPort repositoryPort,
            EncryptionService encryptionService) {
        return new SystemConfigService(repositoryPort, encryptionService);
    }
}
