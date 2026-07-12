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
    @DisplayName("notificaciones-telegram: telegram_group_id is stored in plain text (not encrypted)")
    void should_store_telegram_group_id_in_plain_text() {
        // Arrange
        SystemConfig existingConfig = SystemConfig.builder()
                .id(1L)
                .clubName("Club")
                .clubDescription("Desc")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(4)
                .updatedAt(OffsetDateTime.now())
                .build();

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, 4, null, null, null, "-100999888");

        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(existingConfig));
        when(repositoryPort.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        configService.updateConfig(request);

        // Assert — the group id is persisted as-is (a chat id is not a secret, so no encryption)
        assertThat(existingConfig.getTelegramGroupId()).isEqualTo("-100999888");
        org.mockito.Mockito.verify(encryptionService, org.mockito.Mockito.never())
                .encrypt("-100999888");
    }

    // -------------------------------------------------------------------------
    // updateConfig — full field coverage (change backend-branch-coverage)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update with all secrets + pricing set encrypts every secret and stores the group id in clear")
    void should_update_all_secret_and_pricing_fields() {
        SystemConfig existing = SystemConfig.builder()
                .id(1L).clubName("Old").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH).maxParticipantsPerPista(4)
                .pricePerHour(new java.math.BigDecimal("10.00")).cancellationDeadlineHours(24)
                .updatedAt(OffsetDateTime.now()).build();
        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repositoryPort.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(encryptionService.encrypt("bot-tok")).thenReturn("enc-bot");
        when(encryptionService.encrypt("hook-secret")).thenReturn("enc-hook");
        when(encryptionService.encrypt("merch-id")).thenReturn("enc-id");
        when(encryptionService.encrypt("merch-key")).thenReturn("enc-key");

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "New Club", "New Desc", PistaState.MANTENIMIENTO, PaymentGateway.REDSYS,
                "merch-id", "merch-key", "bot-tok", 6,
                new java.math.BigDecimal("20.00"), 12, "hook-secret", "-100999");

        configService.updateConfig(request);

        // every secret encrypted (true branches of the !=null && !isBlank guards)
        org.mockito.Mockito.verify(encryptionService).encrypt("bot-tok");
        org.mockito.Mockito.verify(encryptionService).encrypt("hook-secret");
        org.mockito.Mockito.verify(encryptionService).encrypt("merch-id");
        org.mockito.Mockito.verify(encryptionService).encrypt("merch-key");
        // group id is not a secret — stored verbatim
        assertThat(existing.getTelegramGroupId()).isEqualTo("-100999");
        // optional pricing fields present → applied
        assertThat(existing.getPricePerHour()).isEqualByComparingTo("20.00");
        assertThat(existing.getCancellationDeadlineHours()).isEqualTo(12);
        assertThat(existing.getMaxParticipantsPerPista()).isEqualTo(6);
    }

    @Test
    @DisplayName("blank secret/group/pricing fields are skipped (isBlank / null branches), keeping stored values")
    void should_skip_blank_or_null_optional_fields() {
        SystemConfig existing = SystemConfig.builder()
                .id(1L).clubName("Old").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH).maxParticipantsPerPista(4)
                .pricePerHour(new java.math.BigDecimal("10.00")).cancellationDeadlineHours(24)
                .telegramGroupId("-100keep").updatedAt(OffsetDateTime.now()).build();
        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repositoryPort.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // blank strings for every secret + group id, null pricing → all guards skip
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "New", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                "  ", "  ", "  ", 4, null, null, "  ", "  ");

        configService.updateConfig(request);

        // nothing encrypted, stored values untouched
        org.mockito.Mockito.verifyNoInteractions(encryptionService);
        assertThat(existing.getPricePerHour()).isEqualByComparingTo("10.00");
        assertThat(existing.getCancellationDeadlineHours()).isEqualTo(24);
        assertThat(existing.getTelegramGroupId()).isEqualTo("-100keep");
    }

    // -------------------------------------------------------------------------
    // validateUpdateRequest — every guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("REDSYS without merchant key → validation error")
    void should_throw_when_redsys_missing_key() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.REDSYS,
                "merch-id", null, null, 4, null, null);

        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("merchant_key");
    }

    @Test
    @DisplayName("null maxParticipants → validation error")
    void should_throw_when_max_participants_null() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maxParticipantsPerPista");
    }

    @Test
    @DisplayName("non-positive maxParticipants → validation error")
    void should_throw_when_max_participants_not_positive() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, 0, null, null);

        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("non-positive pricePerHour → validation error")
    void should_throw_when_price_not_positive() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, 4, java.math.BigDecimal.ZERO, null);

        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("pricePerHour");
    }

    @Test
    @DisplayName("negative cancellationDeadlineHours → validation error")
    void should_throw_when_deadline_negative() {
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club", "Desc", PistaState.ACTIVA, PaymentGateway.CASH,
                null, null, null, 4, new java.math.BigDecimal("10.00"), -1);

        assertThatThrownBy(() -> configService.updateConfig(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cancellationDeadlineHours");
    }

    @Test
    @DisplayName("getConfig throws when the singleton row is missing")
    void should_throw_when_config_missing() {
        when(repositoryPort.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> configService.getConfig())
                .isInstanceOf(RuntimeException.class);
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
