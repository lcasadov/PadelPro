package com.padelpro.pagos.application.service;

/**
 * Decrypted Redsys merchant credentials (pagos-redsys-online, D4). Held only in memory; never logged.
 *
 * @param merchantCode the merchant/commerce code (FUC)
 * @param merchantKey  the merchant secret key (Base64) used to derive per-order signing keys
 * @param terminal     the terminal number (defaults to {@code "1"} when unconfigured)
 */
public record RedsysCredentials(String merchantCode, String merchantKey, String terminal) {
}
