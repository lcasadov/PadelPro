package com.padelpro.mensajeria.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.application.service.TelegramAuditRecorder;
import com.padelpro.mensajeria.application.service.TelegramConfigService;
import com.padelpro.mensajeria.application.service.TelegramWebhookService;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.mensajeria.infrastructure.telegram.TelegramApiAdapter;
import com.padelpro.otp.application.service.OtpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the Telegram messaging side of auth-otp-telegram (D-OTP-01/04/05). Application services
 * are plain objects instantiated here (mirrors {@code PagosConfig}), keeping the application layer
 * free of Spring stereotypes.
 */
@Configuration
public class MensajeriaConfig {

    @Bean
    public TelegramConfigService telegramConfigService(SystemConfigRepositoryPort systemConfigRepositoryPort,
                                                       EncryptionService encryptionService) {
        return new TelegramConfigService(systemConfigRepositoryPort, encryptionService);
    }

    @Bean
    public TelegramAuditRecorder telegramAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                                       UserRepositoryPort userRepositoryPort) {
        return new TelegramAuditRecorder(auditLogRepositoryPort, userRepositoryPort);
    }

    @Bean
    public TelegramPort telegramPort(TelegramConfigService telegramConfigService,
                                     RestClient.Builder restClientBuilder,
                                     @Value("${app.telegram.api-url:https://api.telegram.org}") String apiUrl) {
        return new TelegramApiAdapter(telegramConfigService, restClientBuilder, apiUrl);
    }

    @Bean
    public TelegramWebhookService telegramWebhookService(TelegramConfigService telegramConfigService,
                                                         OtpService otpService,
                                                         UserRepositoryPort userRepositoryPort,
                                                         TelegramAuditRecorder telegramAuditRecorder,
                                                         TelegramPort telegramPort,
                                                         ObjectMapper objectMapper,
                                                         @Value("${app.telegram.profile-url:http://localhost:5173/perfil}") String profileUrl) {
        return new TelegramWebhookService(telegramConfigService, otpService, userRepositoryPort,
                telegramAuditRecorder, telegramPort, objectMapper, profileUrl);
    }
}
