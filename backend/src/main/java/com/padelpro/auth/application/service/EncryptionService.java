package com.padelpro.auth.application.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption service (D-CONF-02).
 *
 * Encrypts secrets (telegram_bot_token, redsys credentials) using AES-256-GCM
 * with random IV. Format: Base64(IV + ciphertext + tag), where tag is part of GCM.
 */
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 128;  // 128 bits (16 bytes)

    private final SecretKey secretKey;

    public EncryptionService(String encryptionKeyString) {
        this.secretKey = deriveKeyFromString(encryptionKeyString);
    }

    /**
     * Encrypt plaintext using AES-256-GCM with random IV.
     * Returns Base64(IV + ciphertext + tag).
     */
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Combine IV + ciphertext (GCM tag is appended by Cipher)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext (Base64(IV + ciphertext + tag)) back to plaintext.
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV from the beginning
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            // Rest is ciphertext + tag
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Derive a 256-bit SecretKey from a string.
     * For production, use a proper KDF (PBKDF2, etc). For testing, pad/truncate to 32 bytes.
     */
    private SecretKey deriveKeyFromString(String keyString) {
        byte[] keyBytes = new byte[32];  // AES-256 = 32 bytes
        byte[] input = keyString.getBytes();

        // Simple padding: repeat string until we have 32 bytes, then truncate
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = input[i % input.length];
        }

        return new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
    }
}
