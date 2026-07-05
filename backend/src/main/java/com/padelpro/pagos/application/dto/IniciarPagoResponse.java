package com.padelpro.pagos.application.dto;

import java.math.BigDecimal;

/**
 * Response of {@code POST /api/pagos/iniciar} (pagos-redsys-online, D5/D6).
 *
 * <p>Carries the three Redsys form fields ({@code dsSignatureVersion}, {@code dsMerchantParameters},
 * {@code dsSignature}) plus {@code redsysUrl} (the TPV endpoint) so the frontend can build a hidden
 * form and auto-submit it to Redsys. Card data never touches PadelPro (RN-PAY-03).
 *
 * @param pagoId             the payment UUID
 * @param redsysOrderId      the generated {@code Ds_Merchant_Order} (unique, RN-PAY-02)
 * @param redsysUrl          the TPV endpoint the form is submitted to
 * @param amount             the charged amount in euros (backend-frozen, RN-RES-03)
 * @param status             the payment status after initiation ({@code IN_PROGRESS})
 * @param dsSignatureVersion always {@code HMAC_SHA256_V1}
 * @param dsMerchantParameters Base64 of the signed parameters JSON
 * @param dsSignature        the HMAC-SHA256 signature (Base64)
 */
public record IniciarPagoResponse(
        String pagoId,
        String redsysOrderId,
        String redsysUrl,
        BigDecimal amount,
        String status,
        String dsSignatureVersion,
        String dsMerchantParameters,
        String dsSignature
) {
}
