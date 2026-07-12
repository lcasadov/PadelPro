package com.padelpro.otp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OtpCode — domain behaviour (RN-AUTH-07)")
class OtpCodeTest {

    private OtpCode newOtp(OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return new OtpCode(1L, "hash", OtpType.TELEGRAM_LINK, expiresAt, now);
    }

    @Test
    @DisplayName("should_be_expired_when_expires_at_in_the_past")
    void should_be_expired_when_expires_at_in_the_past() {
        OtpCode otp = newOtp(OffsetDateTime.now().minusSeconds(1));
        assertThat(otp.isExpired(OffsetDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("should_not_be_expired_when_expires_at_in_the_future")
    void should_not_be_expired_when_expires_at_in_the_future() {
        OtpCode otp = newOtp(OffsetDateTime.now().plusMinutes(10));
        assertThat(otp.isExpired(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("should_mark_used")
    void should_mark_used() {
        OtpCode otp = newOtp(OffsetDateTime.now().plusMinutes(10));
        otp.markUsed();
        assertThat(otp.isUsed()).isTrue();
    }

    @Test
    @DisplayName("should_not_invalidate_before_third_failed_attempt")
    void should_not_invalidate_before_third_failed_attempt() {
        OtpCode otp = newOtp(OffsetDateTime.now().plusMinutes(10));
        assertThat(otp.registerFailedAttempt()).isFalse();
        assertThat(otp.registerFailedAttempt()).isFalse();
        assertThat(otp.getAttempts()).isEqualTo(2);
        assertThat(otp.isUsed()).isFalse();
    }

    @Test
    @DisplayName("should_auto_invalidate_on_third_failed_attempt")
    void should_auto_invalidate_on_third_failed_attempt() {
        OtpCode otp = newOtp(OffsetDateTime.now().plusMinutes(10));
        otp.registerFailedAttempt();
        otp.registerFailedAttempt();
        boolean invalidated = otp.registerFailedAttempt();
        assertThat(invalidated).isTrue();
        assertThat(otp.getAttempts()).isEqualTo(3);
        assertThat(otp.isUsed()).isTrue();
    }
}
