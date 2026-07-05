package com.padelpro.pagos.application.service;

import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.pagos.domain.exception.PagoUnprocessableException;

/**
 * Resolves and decrypts the Redsys merchant credentials from {@code system_config} (pagos-redsys-online,
 * D4). {@code redsys_merchant_id}, {@code redsys_merchant_key} and {@code redsys_terminal} are stored
 * AES-256-GCM encrypted and decrypted here with the existing {@link EncryptionService}.
 *
 * <p>The terminal defaults to {@code "1"} when unconfigured. If merchant id/key are missing the
 * gateway cannot be used → {@link PagoUnprocessableException} {@code REDSYS_NOT_CONFIGURED} (422).
 *
 * <p>Never logs any secret (RN-PAY-03).
 */
public class RedsysConfigService {

    private static final String DEFAULT_TERMINAL = "1";

    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final EncryptionService encryptionService;

    public RedsysConfigService(SystemConfigRepositoryPort systemConfigRepositoryPort,
                               EncryptionService encryptionService) {
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.encryptionService = encryptionService;
    }

    /** Decrypted terminal, defaulting to {@code "1"} when the column is null/blank (D4). */
    public String getTerminalOrDefault() {
        SystemConfig config = loadConfig();
        return decryptTerminal(config);
    }

    /**
     * Full Redsys credentials for building/verifying the TPV form.
     *
     * @throws PagoUnprocessableException {@code REDSYS_NOT_CONFIGURED} if merchant id/key are absent
     */
    public RedsysCredentials loadCredentials() {
        SystemConfig config = loadConfig();
        String merchantId = decryptOrNull(config.getRedsysMerchantId());
        String merchantKey = decryptOrNull(config.getRedsysMerchantKey());
        if (isBlank(merchantId) || isBlank(merchantKey)) {
            throw new PagoUnprocessableException("REDSYS_NOT_CONFIGURED",
                    "El comercio Redsys no está configurado");
        }
        return new RedsysCredentials(merchantId, merchantKey, decryptTerminal(config));
    }

    private SystemConfig loadConfig() {
        return systemConfigRepositoryPort.findById(1L)
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
    }

    private String decryptTerminal(SystemConfig config) {
        String terminal = decryptOrNull(config.getRedsysTerminal());
        return isBlank(terminal) ? DEFAULT_TERMINAL : terminal;
    }

    private String decryptOrNull(String encrypted) {
        if (isBlank(encrypted)) {
            return null;
        }
        return encryptionService.decrypt(encrypted);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
