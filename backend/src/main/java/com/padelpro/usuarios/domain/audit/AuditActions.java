package com.padelpro.usuarios.domain.audit;

/**
 * Audit action constants for the usuarios capability.
 *
 * <p>Known actions from auth-local: USER_REGISTERED, LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_TAMPERED.
 * This class adds the admin-management actions introduced in the usuarios change.
 */
public final class AuditActions {

    public static final String USER_CREATED_BY_ADMIN = "USER_CREATED_BY_ADMIN";
    public static final String USER_APPROVED         = "USER_APPROVED";
    public static final String USER_DEACTIVATED      = "USER_DEACTIVATED";
    public static final String USER_ROLE_CHANGED     = "USER_ROLE_CHANGED";

    private AuditActions() {
        // Utility class — not instantiable
    }
}
