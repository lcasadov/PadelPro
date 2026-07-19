package com.padelpro.otp.application.service;

import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.domain.audit.OtpAuditActions;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core OTP service (auth-otp-telegram, RN-AUTH-07 / RN-RGPD-04).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li><b>generate</b>: 6-digit code, 10-min TTL, stored as SHA-256; issuing a new code of a type
 *       invalidates the previous active code of that same type for the user (D-OTP-03).</li>
 *   <li><b>verify</b>: valid only if not expired, not used and {@code attempts < 3}; a wrong code
 *       increments {@code attempts} and the 3rd failure auto-invalidates the code.</li>
 *   <li><b>Telegram link</b>: resolve/consume a {@code TELEGRAM_LINK} code by its clear value.</li>
 *   <li><b>revoke</b>: invalidate all active codes for a user (Telegram unlink).</li>
 * </ul>
 *
 * <p>The clear code is only ever held in memory to be returned to the caller — it is never logged
 * (RN-RGPD-04) and only its SHA-256 digest is persisted (D-OTP-02).
 */
public class OtpService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int CODE_BOUND = 1_000_000; // 6 digits: 000000..999999

    private final OtpCodeRepositoryPort repository;
    private final OtpAuditRecorder auditRecorder;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(OtpCodeRepositoryPort repository, OtpAuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Generate a fresh OTP for {@code userId} of the given {@code type}, invalidating any prior
     * active code of the same type (RN-AUTH-07 / D-OTP-03).
     *
     * @return the clear code + expiry (never logged); the caller delivers it to the user
     */
    @Transactional
    public GeneratedOtp generate(Long userId, OtpType type) {
        // Invalidate the previous active code of this type to avoid accumulation (spec edge case).
        for (OtpCode previous : repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(userId, type)) {
            previous.markUsed();
            repository.save(previous);
        }

        String code = generateCode();
        OffsetDateTime now = OffsetDateTime.now();
        OtpCode otp = new OtpCode(userId, OtpHasher.sha256Hex(code), type, now.plus(TTL), now);
        repository.save(otp);

        auditRecorder.record(OtpAuditActions.OTP_GENERATED, userId, "type=" + type);
        return new GeneratedOtp(code, otp.getExpiresAt());
    }

    /**
     * Generate a fresh OTP for {@code userId} of {@code type} bound to {@code reservationId}
     * (bot-telegram-reservas, D-4). Invalidates any prior active code bound to the <em>same
     * reservation</em> (any type), so a cancellation request supersedes a pending confirmation for that
     * reservation while leaving codes of <em>other</em> reservations untouched.
     *
     * @return the clear code + expiry (never logged); the caller delivers it to the user
     */
    @Transactional
    public GeneratedOtp generate(Long userId, OtpType type, UUID reservationId) {
        for (OtpCode previous
                : repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(userId, reservationId)) {
            previous.markUsed();
            repository.save(previous);
        }

        String code = generateCode();
        OffsetDateTime now = OffsetDateTime.now();
        OtpCode otp = new OtpCode(userId, OtpHasher.sha256Hex(code), type,
                now.plus(TTL), now, reservationId);
        repository.save(otp);

        auditRecorder.record(OtpAuditActions.OTP_GENERATED, userId,
                "type=" + type + ",reservationId=" + reservationId);
        return new GeneratedOtp(code, otp.getExpiresAt());
    }

    /**
     * Peek (read-only) the type of the active, unexpired code bound to {@code reservationId} for the
     * user — i.e. which operation is pending on that reservation (bot-telegram-reservas, D-4). Records
     * no attempt and consumes nothing, so a caller can decide confirm-vs-cancel <em>before</em>
     * verifying without burning an attempt against a valid code.
     *
     * @return the pending {@link OtpType} for the reservation, or empty if none is active
     */
    @Transactional(readOnly = true)
    public Optional<OtpType> peekActiveReservationType(Long userId, UUID reservationId) {
        OffsetDateTime now = OffsetDateTime.now();
        return repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(userId, reservationId)
                .stream()
                .filter(otp -> !otp.isExpired(now))
                .map(OtpCode::getType)
                .findFirst();
    }

    /**
     * Verify a clear {@code code} against the active code bound to {@code reservationId} for the user
     * (RN-AUTH-07). Marks it used on success; a wrong code increments attempts and auto-invalidates on
     * the 3rd failure — exactly like {@link #verify}, but scoped to a single reservation so the code of
     * one reservation can never be spent against another.
     *
     * @throws OtpVerificationException when the code is invalid, expired or the attempt limit is reached
     */
    @Transactional
    public void verifyForReservation(Long userId, UUID reservationId, String code) {
        List<OtpCode> active =
                repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(userId, reservationId);
        if (active.isEmpty()) {
            auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED, userId,
                    "reservationId=" + reservationId + ",reason=no_active_code");
            throw OtpVerificationException.invalid();
        }

        OtpCode otp = active.get(0);
        if (otp.isExpired(OffsetDateTime.now())) {
            auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED, userId,
                    "reservationId=" + reservationId + ",reason=expired");
            throw OtpVerificationException.expired();
        }

        if (otp.getCodeHash().equals(OtpHasher.sha256Hex(code))) {
            otp.markUsed();
            repository.save(otp);
            auditRecorder.record(OtpAuditActions.OTP_VERIFIED, userId,
                    "type=" + otp.getType() + ",reservationId=" + reservationId);
            return;
        }

        boolean invalidated = otp.registerFailedAttempt();
        repository.save(otp);
        auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED, userId,
                "reservationId=" + reservationId + ",attempts=" + otp.getAttempts());
        if (invalidated) {
            auditRecorder.record(OtpAuditActions.OTP_INVALIDATED, userId,
                    "reservationId=" + reservationId + ",reason=max_attempts");
            throw OtpVerificationException.maxAttempts();
        }
        throw OtpVerificationException.invalid();
    }

    /**
     * Verify a clear {@code code} for {@code userId} + {@code type}. Marks the code used on success.
     *
     * @throws OtpVerificationException (mapped to 422) when the code is invalid, expired or the
     *                                  attempt limit has been reached
     */
    @Transactional
    public void verify(Long userId, OtpType type, String code) {
        List<OtpCode> active = repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(userId, type);
        if (active.isEmpty()) {
            auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED, userId, "type=" + type + ",reason=no_active_code");
            throw OtpVerificationException.invalid();
        }

        OtpCode otp = active.get(0);
        if (otp.isExpired(OffsetDateTime.now())) {
            auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED, userId, "type=" + type + ",reason=expired");
            throw OtpVerificationException.expired();
        }

        if (otp.getCodeHash().equals(OtpHasher.sha256Hex(code))) {
            otp.markUsed();
            repository.save(otp);
            auditRecorder.record(OtpAuditActions.OTP_VERIFIED, userId, "type=" + type);
            return;
        }

        boolean invalidated = otp.registerFailedAttempt();
        repository.save(otp);
        auditRecorder.record(OtpAuditActions.OTP_VERIFICATION_FAILED,
                userId, "type=" + type + ",attempts=" + otp.getAttempts());
        if (invalidated) {
            auditRecorder.record(OtpAuditActions.OTP_INVALIDATED, userId, "type=" + type + ",reason=max_attempts");
            throw OtpVerificationException.maxAttempts();
        }
        throw OtpVerificationException.invalid();
    }

    /**
     * Resolve the active {@code TELEGRAM_LINK} code matching {@code code} (without consuming it),
     * returning the owning entity so the webhook can perform its chat-ownership guard before
     * committing the link (spec Requirement 1).
     *
     * @throws OtpVerificationException if no active matching code exists or it has expired
     */
    @Transactional(readOnly = true)
    public OtpCode resolveActiveLinkOtp(String code) {
        List<OtpCode> matches =
                repository.findByCodeHashAndTypeAndUsedFalse(OtpHasher.sha256Hex(code), OtpType.TELEGRAM_LINK);
        if (matches.isEmpty()) {
            throw OtpVerificationException.invalid();
        }
        OtpCode otp = matches.get(0);
        if (otp.isExpired(OffsetDateTime.now())) {
            throw OtpVerificationException.expired();
        }
        return otp;
    }

    /** Consume (single-use) an already-resolved OTP and audit the verification. */
    @Transactional
    public void consume(OtpCode otp) {
        otp.markUsed();
        repository.save(otp);
        auditRecorder.record(OtpAuditActions.OTP_VERIFIED, otp.getUserId(), "type=" + otp.getType());
    }

    /** Invalidate every active code for a user (Telegram unlink — spec Requirement 3). */
    @Transactional
    public void revokeAllActive(Long userId) {
        for (OtpCode otp : repository.findByUserIdAndUsedFalse(userId)) {
            otp.markUsed();
            repository.save(otp);
        }
        auditRecorder.record(OtpAuditActions.OTP_INVALIDATED, userId, "reason=revoke_all");
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
