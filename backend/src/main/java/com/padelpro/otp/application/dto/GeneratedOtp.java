package com.padelpro.otp.application.dto;

import java.time.OffsetDateTime;

/**
 * Result of generating an OTP: the clear 6-digit code (held only in memory, never persisted or
 * logged — RN-RGPD-04) and its expiry. The caller is responsible for delivering the code to the
 * user through the appropriate channel.
 */
public record GeneratedOtp(String code, OffsetDateTime expiresAt) {
}
