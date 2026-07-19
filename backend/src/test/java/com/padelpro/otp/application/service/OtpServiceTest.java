package com.padelpro.otp.application.service;

import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.domain.audit.OtpAuditActions;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService — unit tests (RN-AUTH-07 / RN-RGPD-04)")
class OtpServiceTest {

    @Mock
    private OtpCodeRepositoryPort repository;

    @Mock
    private OtpAuditRecorder auditRecorder;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(repository, auditRecorder);
        lenient().when(repository.save(any(OtpCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OtpCode otpFor(String code, OtpType type, OffsetDateTime expiresAt) {
        return new OtpCode(42L, OtpHasher.sha256Hex(code), type, expiresAt, OffsetDateTime.now());
    }

    @Test
    @DisplayName("generate stores SHA-256 (not the clear code), 6-digit, 10-min TTL, audits")
    void generate_stores_hash_and_returns_clear_code() {
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.TELEGRAM_LINK))
                .thenReturn(List.of());

        GeneratedOtp result = service.generate(42L, OtpType.TELEGRAM_LINK);

        assertThat(result.code()).matches("\\d{6}");
        assertThat(result.expiresAt()).isAfter(OffsetDateTime.now().plusMinutes(9));

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(repository).save(captor.capture());
        OtpCode saved = captor.getValue();
        assertThat(saved.getCodeHash()).isEqualTo(OtpHasher.sha256Hex(result.code()));
        assertThat(saved.getCodeHash()).isNotEqualTo(result.code());
        assertThat(saved.getType()).isEqualTo(OtpType.TELEGRAM_LINK);
        assertThat(saved.isUsed()).isFalse();
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_GENERATED), eq(42L), any());
    }

    @Test
    @DisplayName("generate invalidates the previous active code of the same type")
    void generate_invalidates_previous_same_type() {
        OtpCode previous = otpFor("111111", OtpType.TELEGRAM_LINK, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.TELEGRAM_LINK))
                .thenReturn(List.of(previous));

        service.generate(42L, OtpType.TELEGRAM_LINK);

        assertThat(previous.isUsed()).isTrue();
        // previous save + new otp save
        verify(repository, times(2)).save(any(OtpCode.class));
    }

    @Test
    @DisplayName("verify marks used and audits on the correct code")
    void verify_success_marks_used() {
        OtpCode otp = otpFor("654321", OtpType.RESERVATION_CONFIRM, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.RESERVATION_CONFIRM))
                .thenReturn(List.of(otp));

        service.verify(42L, OtpType.RESERVATION_CONFIRM, "654321");

        assertThat(otp.isUsed()).isTrue();
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_VERIFIED), eq(42L), any());
    }

    @Test
    @DisplayName("verify increments attempts and keeps the code valid on a wrong code (<3 fails)")
    void verify_wrong_code_increments_attempts() {
        OtpCode otp = otpFor("654321", OtpType.RESERVATION_CONFIRM, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.RESERVATION_CONFIRM))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verify(42L, OtpType.RESERVATION_CONFIRM, "000000"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_INVALID");
        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.isUsed()).isFalse();
    }

    @Test
    @DisplayName("verify auto-invalidates on the third failed attempt (OTP_MAX_ATTEMPTS)")
    void verify_third_fail_invalidates() {
        OtpCode otp = otpFor("654321", OtpType.RESERVATION_CONFIRM, OffsetDateTime.now().plusMinutes(5));
        otp.registerFailedAttempt();
        otp.registerFailedAttempt(); // now at 2 previous failures
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.RESERVATION_CONFIRM))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verify(42L, OtpType.RESERVATION_CONFIRM, "000000"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_MAX_ATTEMPTS");
        assertThat(otp.isUsed()).isTrue();
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_INVALIDATED), eq(42L), any());
    }

    @Test
    @DisplayName("verify throws OTP_EXPIRED for an expired code")
    void verify_expired() {
        OtpCode otp = otpFor("654321", OtpType.RESERVATION_CONFIRM, OffsetDateTime.now().minusSeconds(1));
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.RESERVATION_CONFIRM))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verify(42L, OtpType.RESERVATION_CONFIRM, "654321"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_EXPIRED");
    }

    @Test
    @DisplayName("verify throws OTP_INVALID when no active code exists")
    void verify_no_active_code() {
        when(repository.findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(42L, OtpType.PASSWORD_RESET))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(42L, OtpType.PASSWORD_RESET, "123456"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_INVALID");
    }

    @Test
    @DisplayName("resolveActiveLinkOtp returns the matching non-expired link OTP")
    void resolve_link_otp_ok() {
        OtpCode otp = otpFor("246810", OtpType.TELEGRAM_LINK, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByCodeHashAndTypeAndUsedFalse(OtpHasher.sha256Hex("246810"), OtpType.TELEGRAM_LINK))
                .thenReturn(List.of(otp));

        assertThat(service.resolveActiveLinkOtp("246810")).isSameAs(otp);
    }

    @Test
    @DisplayName("resolveActiveLinkOtp throws OTP_EXPIRED for an expired link OTP")
    void resolve_link_otp_expired() {
        OtpCode otp = otpFor("246810", OtpType.TELEGRAM_LINK, OffsetDateTime.now().minusSeconds(1));
        when(repository.findByCodeHashAndTypeAndUsedFalse(OtpHasher.sha256Hex("246810"), OtpType.TELEGRAM_LINK))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.resolveActiveLinkOtp("246810"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_EXPIRED");
    }

    @Test
    @DisplayName("resolveActiveLinkOtp throws OTP_INVALID when no match")
    void resolve_link_otp_invalid() {
        when(repository.findByCodeHashAndTypeAndUsedFalse(any(), eq(OtpType.TELEGRAM_LINK)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveActiveLinkOtp("999999"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_INVALID");
    }

    @Test
    @DisplayName("consume marks the OTP used and audits verification")
    void consume_marks_used() {
        OtpCode otp = otpFor("246810", OtpType.TELEGRAM_LINK, OffsetDateTime.now().plusMinutes(5));
        service.consume(otp);
        assertThat(otp.isUsed()).isTrue();
        verify(repository).save(otp);
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_VERIFIED), eq(42L), any());
    }

    // -------------------------------------------------------------------------
    // Reservation-bound codes (bot-telegram-reservas, D-4)
    // -------------------------------------------------------------------------

    private static final UUID RES = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");

    private OtpCode reservationOtp(String code, OtpType type, OffsetDateTime expiresAt, UUID reservationId) {
        return new OtpCode(42L, OtpHasher.sha256Hex(code), type, expiresAt, OffsetDateTime.now(), reservationId);
    }

    @Test
    @DisplayName("generate(type, reservationId) stores the code bound to the reservation and audits it")
    void generate_bound_to_reservation() {
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of());

        GeneratedOtp result = service.generate(42L, OtpType.RESERVATION_CONFIRM, RES);

        assertThat(result.code()).matches("\\d{6}");
        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReservationId()).isEqualTo(RES);
        assertThat(captor.getValue().getType()).isEqualTo(OtpType.RESERVATION_CONFIRM);
    }

    @Test
    @DisplayName("generate(type, reservationId) invalidates the previous code of the SAME reservation only")
    void generate_bound_invalidates_same_reservation() {
        OtpCode previous = reservationOtp("111111", OtpType.RESERVATION_CONFIRM,
                OffsetDateTime.now().plusMinutes(5), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(previous));

        service.generate(42L, OtpType.CANCELLATION_CONFIRM, RES);

        assertThat(previous.isUsed()).isTrue();
        verify(repository, times(2)).save(any(OtpCode.class));
    }

    @Test
    @DisplayName("peekActiveReservationType returns the active code's type without consuming it")
    void peek_returns_type() {
        OtpCode otp = reservationOtp("246810", OtpType.CANCELLATION_CONFIRM,
                OffsetDateTime.now().plusMinutes(5), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        assertThat(service.peekActiveReservationType(42L, RES))
                .contains(OtpType.CANCELLATION_CONFIRM);
        assertThat(otp.isUsed()).isFalse();
        assertThat(otp.getAttempts()).isZero();
    }

    @Test
    @DisplayName("peekActiveReservationType ignores an expired code and returns empty")
    void peek_ignores_expired() {
        OtpCode otp = reservationOtp("246810", OtpType.RESERVATION_CONFIRM,
                OffsetDateTime.now().minusSeconds(1), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        assertThat(service.peekActiveReservationType(42L, RES)).isEmpty();
    }

    @Test
    @DisplayName("verifyForReservation consumes the reservation's code on success")
    void verify_for_reservation_success() {
        OtpCode otp = reservationOtp("654321", OtpType.RESERVATION_CONFIRM,
                OffsetDateTime.now().plusMinutes(5), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        service.verifyForReservation(42L, RES, "654321");

        assertThat(otp.isUsed()).isTrue();
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_VERIFIED), eq(42L), any());
    }

    @Test
    @DisplayName("verifyForReservation increments attempts on a wrong code (<3 fails)")
    void verify_for_reservation_wrong_code() {
        OtpCode otp = reservationOtp("654321", OtpType.RESERVATION_CONFIRM,
                OffsetDateTime.now().plusMinutes(5), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verifyForReservation(42L, RES, "000000"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_INVALID");
        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.isUsed()).isFalse();
    }

    @Test
    @DisplayName("verifyForReservation auto-invalidates on the third failed attempt")
    void verify_for_reservation_third_fail() {
        OtpCode otp = reservationOtp("654321", OtpType.CANCELLATION_CONFIRM,
                OffsetDateTime.now().plusMinutes(5), RES);
        otp.registerFailedAttempt();
        otp.registerFailedAttempt();
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verifyForReservation(42L, RES, "000000"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_MAX_ATTEMPTS");
        assertThat(otp.isUsed()).isTrue();
    }

    @Test
    @DisplayName("verifyForReservation throws OTP_EXPIRED for an expired reservation code")
    void verify_for_reservation_expired() {
        OtpCode otp = reservationOtp("654321", OtpType.RESERVATION_CONFIRM,
                OffsetDateTime.now().minusSeconds(1), RES);
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of(otp));

        assertThatThrownBy(() -> service.verifyForReservation(42L, RES, "654321"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_EXPIRED");
    }

    @Test
    @DisplayName("verifyForReservation throws OTP_INVALID when the reservation has no active code")
    void verify_for_reservation_no_active_code() {
        when(repository.findByUserIdAndReservationIdAndUsedFalseOrderByCreatedAtDesc(42L, RES))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.verifyForReservation(42L, RES, "123456"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("code").isEqualTo("OTP_INVALID");
    }

    @Test
    @DisplayName("revokeAllActive invalidates every active code for the user")
    void revoke_all_active() {
        OtpCode a = otpFor("111111", OtpType.TELEGRAM_LINK, OffsetDateTime.now().plusMinutes(5));
        OtpCode b = otpFor("222222", OtpType.RESERVATION_CONFIRM, OffsetDateTime.now().plusMinutes(5));
        when(repository.findByUserIdAndUsedFalse(42L)).thenReturn(List.of(a, b));

        service.revokeAllActive(42L);

        assertThat(a.isUsed()).isTrue();
        assertThat(b.isUsed()).isTrue();
        verify(auditRecorder).record(eq(OtpAuditActions.OTP_INVALIDATED), eq(42L), any());
    }
}
