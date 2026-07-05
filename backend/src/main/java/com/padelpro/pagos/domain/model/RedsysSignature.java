package com.padelpro.pagos.domain.model;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Redsys {@code HMAC_SHA256_V1} signature (pagos-redsys-online, D2).
 *
 * <p>Protocol (no third-party dependency, algorithm is short and auditable):
 * <ol>
 *   <li>Derive a per-order key by encrypting the {@code Ds_Merchant_Order} with <b>3DES/CBC</b> and a
 *       zero IV, using the merchant secret key (Base64-decoded to 24 bytes) as the DESede key. The
 *       order is zero-padded to a multiple of 8 bytes (CBC/NoPadding).</li>
 *   <li>Compute <b>HMAC-SHA256</b> of the {@code Ds_MerchantParameters} string (the Base64 of the JSON,
 *       exactly as transmitted) with that per-order key.</li>
 *   <li>Base64-encode the MAC. The request uses standard Base64; the notification uses the URL-safe
 *       alphabet ({@code -_}).</li>
 * </ol>
 *
 * <p>Signature comparison on the webhook path is done in <b>constant time</b>
 * ({@link MessageDigest#isEqual}) to avoid timing side channels (RN-PAY-01).
 *
 * <p><b>Security:</b> this class never logs the merchant key, the derived key, or the parameters.
 */
public final class RedsysSignature {

    private static final byte[] ZERO_IV = new byte[8];

    private RedsysSignature() {
    }

    /**
     * Sign {@code Ds_MerchantParameters} for a <b>request</b> to the TPV (standard Base64 signature).
     *
     * @param base64MerchantParameters the Base64 of the parameters JSON, exactly as it will be sent
     * @param order                    the {@code Ds_Merchant_Order} carried inside those parameters
     * @param merchantKeyBase64        the merchant secret key (Base64)
     * @return the Base64 (standard) {@code Ds_Signature}
     */
    public static String sign(String base64MerchantParameters, String order, String merchantKeyBase64) {
        byte[] derivedKey = deriveOrderKey(order, merchantKeyBase64);
        byte[] mac = hmacSha256(derivedKey, base64MerchantParameters);
        return Base64.getEncoder().encodeToString(mac);
    }

    /**
     * Verify a <b>notification</b> signature in constant time (RN-PAY-01). The received
     * {@code Ds_MerchantParameters} and {@code Ds_Signature} may use the URL-safe Base64 alphabet;
     * both URL-safe and standard signatures are accepted.
     *
     * @param receivedMerchantParameters the {@code Ds_MerchantParameters} string exactly as received
     * @param order                      the {@code Ds_Order} decoded from those parameters
     * @param receivedSignature          the {@code Ds_Signature} exactly as received
     * @param merchantKeyBase64          the merchant secret key (Base64)
     * @return {@code true} iff the signature matches
     */
    public static boolean verify(String receivedMerchantParameters, String order,
                                 String receivedSignature, String merchantKeyBase64) {
        if (receivedMerchantParameters == null || order == null || receivedSignature == null) {
            return false;
        }
        byte[] derivedKey = deriveOrderKey(order, merchantKeyBase64);
        byte[] computed = hmacSha256(derivedKey, receivedMerchantParameters);
        byte[] provided;
        try {
            // Redsys emits URL-safe Base64 in notifications; normalise so both variants decode.
            provided = Base64.getUrlDecoder().decode(toUrlSafe(receivedSignature));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Constant-time comparison (RN-PAY-01).
        return MessageDigest.isEqual(computed, provided);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static byte[] deriveOrderKey(String order, String merchantKeyBase64) {
        try {
            byte[] key = Base64.getDecoder().decode(merchantKeyBase64);
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"),
                    new IvParameterSpec(ZERO_IV));
            return cipher.doFinal(zeroPad(order.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Never include the key or order in the message.
            throw new IllegalStateException("Redsys order-key derivation failed", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Redsys HMAC computation failed", e);
        }
    }

    /** Zero-pad to the next multiple of 8 bytes (3DES/CBC block size). */
    private static byte[] zeroPad(byte[] input) {
        int padded = ((input.length + 7) / 8) * 8;
        if (padded == 0) {
            padded = 8;
        }
        return Arrays.copyOf(input, padded);
    }

    private static String toUrlSafe(String s) {
        return s.replace('+', '-').replace('/', '_');
    }
}
