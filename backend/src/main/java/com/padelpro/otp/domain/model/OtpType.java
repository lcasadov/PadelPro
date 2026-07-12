package com.padelpro.otp.domain.model;

/**
 * The purpose an OTP is issued for (auth-otp-telegram, RN-AUTH-07).
 *
 * <p>Each critical operation uses its own type so a code issued for one purpose can never be
 * replayed against another (verification always matches {@code user_id + type}).
 */
public enum OtpType {

    /** Two-step Telegram account linking (spec Requirement 1, RN-TEL-02). */
    TELEGRAM_LINK,

    /** Confirmation of a reservation. */
    RESERVATION_CONFIRM,

    /** Confirmation of a reservation cancellation. */
    CANCELLATION_CONFIRM,

    /** Password reset (consumed by auth-local). */
    PASSWORD_RESET
}
