package com.padelpro.pagos.application.service;

import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.pagos.domain.exception.PagoUnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RedsysConfigService} — decrypting Redsys credentials from {@code system_config}
 * with the real {@link EncryptionService} (round-trip), and the terminal default (D4).
 */
@DisplayName("RedsysConfigService — credenciales Redsys descifradas (D4)")
class RedsysConfigServiceTest {

    private EncryptionService encryptionService;
    private StubConfigRepo repo;
    private RedsysConfigService service;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("test-encryption-key-padelpro-256-bits-minimum-length");
        repo = new StubConfigRepo();
        service = new RedsysConfigService(repo, encryptionService);
    }

    private SystemConfig.SystemConfigBuilder baseConfig() {
        return SystemConfig.builder()
                .id(1L).clubName("Club").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.REDSYS).maxParticipantsPerPista(4)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("terminal descifrado cuando está configurado")
    void decrypts_terminal_when_present() {
        repo.config = baseConfig()
                .redsysMerchantId(encryptionService.encrypt("999008881"))
                .redsysMerchantKey(encryptionService.encrypt("sq7HjrUOBfKmC576ILgskD5srU870gJ7"))
                .redsysTerminal(encryptionService.encrypt("3"))
                .build();

        assertThat(service.getTerminalOrDefault()).isEqualTo("3");
        assertThat(service.loadCredentials().terminal()).isEqualTo("3");
    }

    @Test
    @DisplayName("terminal por defecto \"1\" cuando la columna es null")
    void defaults_terminal_to_1_when_absent() {
        repo.config = baseConfig()
                .redsysMerchantId(encryptionService.encrypt("999008881"))
                .redsysMerchantKey(encryptionService.encrypt("sq7HjrUOBfKmC576ILgskD5srU870gJ7"))
                .redsysTerminal(null)
                .build();

        assertThat(service.getTerminalOrDefault()).isEqualTo("1");
        assertThat(service.loadCredentials().terminal()).isEqualTo("1");
    }

    @Test
    @DisplayName("loadCredentials descifra merchant id/key")
    void decrypts_merchant_credentials() {
        repo.config = baseConfig()
                .redsysMerchantId(encryptionService.encrypt("999008881"))
                .redsysMerchantKey(encryptionService.encrypt("sq7HjrUOBfKmC576ILgskD5srU870gJ7"))
                .build();

        RedsysCredentials creds = service.loadCredentials();
        assertThat(creds.merchantCode()).isEqualTo("999008881");
        assertThat(creds.merchantKey()).isEqualTo("sq7HjrUOBfKmC576ILgskD5srU870gJ7");
    }

    @Test
    @DisplayName("credenciales ausentes → 422 REDSYS_NOT_CONFIGURED")
    void throws_when_not_configured() {
        repo.config = baseConfig().build();

        assertThatThrownBy(() -> service.loadCredentials())
                .isInstanceOf(PagoUnprocessableException.class)
                .extracting(e -> ((PagoUnprocessableException) e).getCode())
                .isEqualTo("REDSYS_NOT_CONFIGURED");
    }

    /** Minimal in-memory stub of the config port. */
    private static class StubConfigRepo implements SystemConfigRepositoryPort {
        SystemConfig config;

        @Override
        public Optional<SystemConfig> findById(Long id) {
            return Optional.ofNullable(config);
        }

        @Override
        public SystemConfig save(SystemConfig config) {
            this.config = config;
            return config;
        }
    }
}
