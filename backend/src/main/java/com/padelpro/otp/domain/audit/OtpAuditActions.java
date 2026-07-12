package com.padelpro.otp.domain.audit;

/**
 * Audit action constants for the auth-otp-telegram capability. Written to {@code audit_log}.
 * The clear OTP code is NEVER part of the audit details (RN-RGPD-04).
 */
public final class OtpAuditActions {

    public static final String OTP_GENERATED           = "OTP_GENERATED";
    public static final String OTP_VERIFIED            = "OTP_VERIFIED";
    public static final String OTP_VERIFICATION_FAILED = "OTP_VERIFICATION_FAILED";
    public static final String OTP_INVALIDATED         = "OTP_INVALIDATED";

    private OtpAuditActions() {
    }
}
