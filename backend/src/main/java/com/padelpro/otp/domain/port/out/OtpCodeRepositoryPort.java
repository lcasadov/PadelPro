package com.padelpro.otp.domain.port.out;

import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;

import java.util.List;

/**
 * Outbound port — persistence operations on {@link OtpCode} (hexagonal).
 *
 * <p>The application layer depends on this interface, never on the Spring Data adapter.
 */
public interface OtpCodeRepositoryPort {

    OtpCode save(OtpCode otpCode);

    /**
     * Active (not-used) codes for a user of a given type, newest first. At most one is expected
     * because {@code generate} invalidates the previous same-type code, but a list keeps the query
     * total and lets the caller pick the freshest deterministically.
     */
    List<OtpCode> findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(Long userId, OtpType type);

    /** All active (not-used) codes for a user, any type — used to revoke on Telegram unlink. */
    List<OtpCode> findByUserIdAndUsedFalse(Long userId);

    /**
     * Active codes matching a code hash + type — used by the Telegram webhook to resolve which
     * account a {@code /vincular <code>} command belongs to (D-OTP-04).
     */
    List<OtpCode> findByCodeHashAndTypeAndUsedFalse(String codeHash, OtpType type);
}
