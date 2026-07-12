package com.padelpro.otp.domain.exception;

/**
 * Raised when an OTP verification fails (auth-otp-telegram, RN-AUTH-07). Mapped to HTTP 422 by the
 * global exception handler, carrying a machine-readable {@code code} and a user-safe message that
 * never leaks whether the failure was a wrong code, an expired code or an unknown user beyond the
 * documented codes below.
 *
 * <p>Codes: {@code OTP_INVALID} (no active code / wrong code / already used),
 * {@code OTP_EXPIRED} (TTL elapsed), {@code OTP_MAX_ATTEMPTS} (auto-invalidated after 3 failures).
 */
public class OtpVerificationException extends RuntimeException {

    private final String code;

    public OtpVerificationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static OtpVerificationException invalid() {
        return new OtpVerificationException("OTP_INVALID", "El código es inválido o ya ha sido utilizado");
    }

    public static OtpVerificationException expired() {
        return new OtpVerificationException("OTP_EXPIRED", "El código ha expirado. Solicita uno nuevo");
    }

    public static OtpVerificationException maxAttempts() {
        return new OtpVerificationException("OTP_MAX_ATTEMPTS",
                "El código ha sido invalidado por exceso de intentos. Solicita uno nuevo");
    }
}
