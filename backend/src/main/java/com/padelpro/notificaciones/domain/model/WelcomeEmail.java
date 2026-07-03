package com.padelpro.notificaciones.domain.model;

/**
 * Value object describing a welcome email to be sent when a user account becomes ACTIVE
 * (change {@code usuarios-alta-edicion-email}, D3).
 *
 * <p>The content differs by activation flow:
 * <ul>
 *   <li><b>Direct creation (admin alta):</b> the system generates a temporary password and it is
 *       included in the email so the user can log in. {@link #temporaryPassword()} is non-null.</li>
 *   <li><b>Approval of a self-registered PENDING account:</b> the user keeps the password they chose
 *       at registration; no password is included. {@link #temporaryPassword()} is {@code null}.</li>
 * </ul>
 *
 * <p>The temporary password, when present, is sensitive: it MUST NOT be written to logs
 * (RN-RGPD-04). Use {@link #hasPassword()} to branch on the flow without exposing the value.
 *
 * @param recipientEmail    destination address (the user's email)
 * @param recipientName     display name for the greeting (typically the first name)
 * @param temporaryPassword the temporary password to communicate, or {@code null} when the user
 *                          already has their own password (approval flow)
 */
public record WelcomeEmail(
        String recipientEmail,
        String recipientName,
        String temporaryPassword
) {

    /** Welcome email for the direct-creation flow, carrying the system-generated password. */
    public static WelcomeEmail withPassword(String recipientEmail, String recipientName,
                                            String temporaryPassword) {
        return new WelcomeEmail(recipientEmail, recipientName, temporaryPassword);
    }

    /** Welcome email for the approval flow: "your account is approved", no password. */
    public static WelcomeEmail accountApproved(String recipientEmail, String recipientName) {
        return new WelcomeEmail(recipientEmail, recipientName, null);
    }

    /** @return {@code true} if this email carries a temporary password (direct-creation flow). */
    public boolean hasPassword() {
        return temporaryPassword != null && !temporaryPassword.isBlank();
    }
}
