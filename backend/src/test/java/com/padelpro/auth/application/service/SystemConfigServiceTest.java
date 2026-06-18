package com.padelpro.auth.application.service;

import com.padelpro.auth.application.dto.SystemConfigResponse;
import com.padelpro.auth.application.dto.UpdateSystemConfigRequest;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * T-025 — Unit tests for SystemConfigService.
 *
 * <p>TDD RED phase: tests establish contract for config retrieval without secrets,
 * validation rules, and encryption/decryption.
 * Scenarios: D-CONF-04 (response without secrets), D-CONF-05 (REDSYS validation),
 * D-CONF-08 (decryption in service).
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepositoryPort repositoryPort;

    @Mock
    private EncryptionService encryptionService;

    private SystemConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new SystemConfigService(repositoryPort, encryptionService);
    }

    // -------------------------------------------------------------------------
    // D-CONF-04 — Response without secrets
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2.3: should return config without secrets (masked as true/false)")
    void should_return_config_without_secrets() {
        // Arrange
        SystemConfig config = SystemConfig.builder()
                .id(1L)
                .clubName("Mi Club de Pádel")
                .clubDescription("Club profesional")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .telegramBotToken("encrypted-token-xyz")
                .redsysMerchantId("encrypted-id-123")
                .redsysMerchantKey("encrypted-key-456")
                .maxParticipantsPerPista(4)
                .updatedAt(OffsetDateTime.now())
                .updatedByUserId(null)
                .build();

        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(config));

        // Act
        SystemConfigResponse response = configService.getConfig();

        // Assert — secrets must be masked as true/false, not plaintext
        assertThat(response.clubName()).isEqualTo("Mi Club de Pádel");
        assertThat(response.telegramBotConfigured()).isTrue();
        assertThat(response.redsysConfigured()).isTrue();
        assertThat(response.pistaState()).isEqualTo(PistaState.ACTIVA);
        assertThat(response.paymentGateway()).isEqualTo(PaymentGateway.CASH);
    }

    // -------------------------------------------------------------------------
    // D-CONF-05 — Validation: REDSYS requires credentials
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2.4: should throw validation error when REDSYS missing credentials")
    void should_throw_validation_error_when_redsys_missing_credentials() {
        // Arrange
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club Name",
                "Description",
                PistaState.ACTIVA,
                PaymentGateway.REDSYS,
                null,  // Missing merchant ID
                "merchant-key",
                "telegram-token",
                4,
                null,
                null
        );

        // Act & Assert
        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REDSYS")
                .hasMessageContaining("merchant");
    }

    @Test
    @DisplayName("2.5: should encrypt secrets on update")
    void should_encrypt_secrets_on_update() {
        // Arrange
        SystemConfig existingConfig = SystemConfig.builder()
                .id(1L)
                .clubName("Old Name")
                .clubDescription("Old Description")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .telegramBotToken(null)
                .redsysMerchantId(null)
                .redsysMerchantKey(null)
                .maxParticipantsPerPista(4)
                .updatedAt(OffsetDateTime.now())
                .updatedByUserId(null)
                .build();

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "New Club Name",
                "New Description",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                null,
                null,
                "new-telegram-token-xyz",
                4,
                null,
                null
        );

        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(existingConfig));
        when(encryptionService.encrypt("new-telegram-token-xyz"))
                .thenReturn("encrypted-new-token");
        when(repositoryPort.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        configService.updateConfig(request);

        // Assert — encryption service must be called
        org.mockito.Mockito.verify(encryptionService).encrypt("new-telegram-token-xyz");
    }

    @Test
    @DisplayName("2.6: should not expose secrets when they are not configured")
    void should_not_expose_secrets_when_not_configured() {
        // Arrange
        SystemConfig config = SystemConfig.builder()
                .id(1L)
                .clubName("Club")
                .clubDescription("Desc")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .telegramBotToken(null)
                .redsysMerchantId(null)
                .redsysMerchantKey(null)
                .maxParticipantsPerPista(4)
                .updatedAt(OffsetDateTime.now())
                .updatedByUserId(null)
                .build();

        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(config));

        // Act
        SystemConfigResponse response = configService.getConfig();

        // Assert — secrets marked as not configured
        assertThat(response.telegramBotConfigured()).isFalse();
        assertThat(response.redsysConfigured()).isFalse();
    }
}
