package com.padelpro.pagos.domain.audit;

/**
 * Audit action constants for the pagos-redsys-online capability. Written to {@code audit_log} with no
 * card data (RN-PAY-03).
 */
public final class PagoAuditActions {

    public static final String PAYMENT_INITIATED                 = "PAYMENT_INITIATED";
    public static final String PAYMENT_CONFIRMED                 = "PAYMENT_CONFIRMED";
    public static final String PAYMENT_REJECTED                  = "PAYMENT_REJECTED";
    public static final String PAYMENT_WEBHOOK_INVALID_SIGNATURE = "PAYMENT_WEBHOOK_INVALID_SIGNATURE";
    public static final String PAYMENT_WEBHOOK_ORDER_NOT_FOUND   = "PAYMENT_WEBHOOK_ORDER_NOT_FOUND";
    public static final String PAYMENT_CASH_REGISTERED           = "PAYMENT_CASH_REGISTERED";
    public static final String PAYMENT_SIMULATED_APPROVED         = "PAYMENT_SIMULATED_APPROVED";
    public static final String PAYMENT_SIMULATED_DECLINED         = "PAYMENT_SIMULATED_DECLINED";

    private PagoAuditActions() {
    }
}
