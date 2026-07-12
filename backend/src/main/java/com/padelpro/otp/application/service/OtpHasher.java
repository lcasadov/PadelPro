package com.padelpro.otp.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 digest helper for OTP codes (D-OTP-02, RN-AUTH-07). The clear code is hashed to a 64-char
 * lowercase hex string that is what gets persisted / compared — the code itself is never stored.
 */
final class OtpHasher {

    private OtpHasher() {
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present in every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
