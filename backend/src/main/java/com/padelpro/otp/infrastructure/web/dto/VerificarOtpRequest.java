package com.padelpro.otp.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/otp/verificar}.
 *
 * @param otpCode the clear 6-digit code the user received
 * @param type    the OTP type (one of {@code TELEGRAM_LINK|RESERVATION_CONFIRM|CANCELLATION_CONFIRM|PASSWORD_RESET})
 */
public record VerificarOtpRequest(
        @NotBlank(message = "otpCode is required") String otpCode,
        @NotBlank(message = "type is required") String type
) {
}
