package com.padelpro.pagos.application.service;

/**
 * Non-secret Redsys endpoint configuration (pagos-redsys-online, D5/D6). Populated from
 * {@code app.redsys.*}. Distinct from the encrypted merchant credentials in {@code system_config}.
 *
 * @param tpvUrl      the Redsys TPV endpoint the signed form is submitted to (sandbox vs production)
 * @param merchantUrl the server-to-server webhook URL sent as {@code Ds_Merchant_MerchantURL}
 * @param urlOk       the browser return URL on success ({@code Ds_Merchant_UrlOK})
 * @param urlKo       the browser return URL on failure ({@code Ds_Merchant_UrlKO})
 * @param currency    ISO-4217 numeric currency code ({@code 978} = EUR)
 */
public record RedsysProperties(
        String tpvUrl,
        String merchantUrl,
        String urlOk,
        String urlKo,
        String currency
) {
}
