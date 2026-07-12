package com.padelpro.otp.infrastructure.web.dto;

/**
 * Success body for {@code POST /api/otp/verificar}. Failures surface as the standard error contract
 * ({@code {error,message,timestamp}}) with HTTP 422 via the global exception handler.
 */
public record VerificarOtpResponse(boolean verified) {
}
