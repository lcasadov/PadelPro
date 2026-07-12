package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.UpdateSystemConfigRequest;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemConfigService — Telegram webhook secret encryption (auth-otp-telegram, D-OTP-01)")
class SystemConfigWebhookSecretTest {

    @Mock
    private SystemConfigRepositoryPort repositoryPort;

    @Mock
    private EncryptionService encryptionService;

    private SystemConfig baseConfig() {
        return SystemConfig.builder()
                .id(1L)
                .clubName("Club")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(4)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("encrypts telegram_webhook_secret before persisting")
    void encrypts_webhook_secret() {
        SystemConfig config = baseConfig();
        when(repositoryPort.findById(1L)).thenReturn(Optional.of(config));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(encryptionService.encrypt("raw-secret")).thenReturn("enc-secret");

        SystemConfigService service = new SystemConfigService(repositoryPort, encryptionService);
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "D", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, 4, null, null, "raw-secret");

        service.updateConfig(request);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repositoryPort).save(captor.capture());
        assertThat(captor.getValue().getTelegramWebhookSecret()).isEqualTo("enc-secret");
    }
}
