package com.padelpro.auth.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-024 — Unit tests for EncryptionService (AES-256-GCM).
 *
 * <p>TDD RED phase: tests establish contract for encrypt/decrypt with IV randomization.
 * Scenarios: D-CONF-02 (AES-256-GCM with random IV), D-CONF-01 (ENCRYPTION_KEY from env).
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Use a 32-character key for AES-256 (test key, not production)
        String testEncryptionKey = "ThisIsA32CharacterTestKeyFor256BitAES";
        encryptionService = new EncryptionService(testEncryptionKey);
    }

    // -------------------------------------------------------------------------
    // D-CONF-02 — AES-256-GCM encryption and decryption
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2.1: should encrypt and decrypt with AES-256-GCM correctly")
    void should_encrypt_and_decrypt_with_aes_256_gcm() {
        // Arrange
        String plaintext = "super-secret-telegram-token-12345";

        // Act
        String ciphertext = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(ciphertext);

        // Assert
        assertThat(decrypted)
                .as("Decrypted text must match original plaintext")
                .isEqualTo(plaintext);
        assertThat(ciphertext)
                .as("Ciphertext must not be empty")
                .isNotBlank()
                .as("Ciphertext must not equal plaintext (encrypted)")
                .isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("2.2: should produce different ciphertext for same plaintext (IV randomization)")
    void should_have_different_ciphertext_for_same_plaintext() {
        // Arrange
        String plaintext = "merchant-key-redsys-abc123";

        // Act — encrypt same plaintext multiple times
        String ciphertext1 = encryptionService.encrypt(plaintext);
        String ciphertext2 = encryptionService.encrypt(plaintext);
        String ciphertext3 = encryptionService.encrypt(plaintext);

        // Assert — each ciphertext should be different (due to random IV)
        assertThat(ciphertext1)
                .as("Ciphertext 1 must not equal ciphertext 2 (random IV)")
                .isNotEqualTo(ciphertext2);
        assertThat(ciphertext2)
                .as("Ciphertext 2 must not equal ciphertext 3 (random IV)")
                .isNotEqualTo(ciphertext3);
        assertThat(ciphertext1)
                .as("Ciphertext 1 must not equal ciphertext 3 (random IV)")
                .isNotEqualTo(ciphertext3);

        // But all must decrypt to the same plaintext
        assertThat(encryptionService.decrypt(ciphertext1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(ciphertext2)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(ciphertext3)).isEqualTo(plaintext);
    }
}
