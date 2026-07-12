package com.padelpro.mensajeria.application.service;

import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramConfigService — decrypts secrets from system_config (D-OTP-01)")
class TelegramConfigServiceTest {

    @Mock
    private SystemConfigRepositoryPort repositoryPort;

    @Mock
    private EncryptionService encryptionService;

    private TelegramConfigService service() {
        return new TelegramConfigService(repositoryPort, encryptionService);
    }

    @Test
    @DisplayName("returns decrypted bot token and webhook secret when configured")
    void returns_decrypted_values() {
        SystemConfig config = SystemConfig.builder()
                .id(1L)
                .telegramBotToken("enc-token")
                .telegramWebhookSecret("enc-secret")
                .build();
        when(repositoryPort.findById(1L)).thenReturn(Optional.of(config));
        when(encryptionService.decrypt("enc-token")).thenReturn("real-token");
        when(encryptionService.decrypt("enc-secret")).thenReturn("real-secret");

        assertThat(service().getBotToken()).contains("real-token");
        assertThat(service().getWebhookSecret()).contains("real-secret");
    }

    @Test
    @DisplayName("returns empty when secrets are not set (RN-TEL-03 degradation)")
    void returns_empty_when_unset() {
        SystemConfig config = SystemConfig.builder().id(1L).build();
        lenient().when(repositoryPort.findById(1L)).thenReturn(Optional.of(config));

        assertThat(service().getBotToken()).isEmpty();
        assertThat(service().getWebhookSecret()).isEmpty();
    }

    @Test
    @DisplayName("returns empty when config row is absent")
    void returns_empty_when_no_config() {
        when(repositoryPort.findById(1L)).thenReturn(Optional.empty());

        assertThat(service().getBotToken()).isEmpty();
        assertThat(service().getWebhookSecret()).isEmpty();
    }
}
