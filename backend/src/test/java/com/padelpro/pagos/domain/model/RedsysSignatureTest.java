package com.padelpro.pagos.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedsysSignature} against the Redsys <b>sandbox</b> (test-environment) merchant.
 *
 * <p>Merchant code {@code 999008881}, terminal {@code 1}, sandbox secret key (Base64)
 * {@code sq7HjrUOBfKmC576ILgskD5srU870gJ7}. The key is public documentation for the test environment,
 * so it is safe to embed here — production keys live encrypted in {@code system_config}.
 */
@DisplayName("RedsysSignature — HMAC_SHA256_V1 (sandbox)")
class RedsysSignatureTest {

    private static final String SANDBOX_KEY = "sq7HjrUOBfKmC576ILgskD5srU870gJ7";
    private static final String MERCHANT_CODE = "999008881";
    private static final String TERMINAL = "1";

    /** Build a realistic Ds_MerchantParameters (Base64 of a request JSON) for a given order. */
    private String buildParams(String order) {
        String json = "{"
                + "\"DS_MERCHANT_AMOUNT\":\"1500\","
                + "\"DS_MERCHANT_ORDER\":\"" + order + "\","
                + "\"DS_MERCHANT_MERCHANTCODE\":\"" + MERCHANT_CODE + "\","
                + "\"DS_MERCHANT_CURRENCY\":\"978\","
                + "\"DS_MERCHANT_TRANSACTIONTYPE\":\"0\","
                + "\"DS_MERCHANT_TERMINAL\":\"" + TERMINAL + "\","
                + "\"DS_MERCHANT_MERCHANTURL\":\"https://example.test/api/pagos/webhook\","
                + "\"DS_MERCHANT_URLOK\":\"https://example.test/pago/confirmado\","
                + "\"DS_MERCHANT_URLKO\":\"https://example.test/pago/confirmado\""
                + "}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("firma determinista: el mismo input produce siempre la misma firma")
    void sign_is_deterministic() {
        String params = buildParams("0001abcd1234");

        String sig1 = RedsysSignature.sign(params, "0001abcd1234", SANDBOX_KEY);
        String sig2 = RedsysSignature.sign(params, "0001abcd1234", SANDBOX_KEY);

        assertThat(sig1).isNotBlank();
        assertThat(sig1).isEqualTo(sig2);
        // The signature is the Base64 of a 32-byte HMAC-SHA256 digest.
        assertThat(Base64.getDecoder().decode(sig1)).hasSize(32);
    }

    @Test
    @DisplayName("round-trip: firmar (request) → verificar (webhook) con la misma clave sandbox")
    void round_trip_sign_then_verify() {
        String order = "0002ffee5678";
        String params = buildParams(order);

        String signature = RedsysSignature.sign(params, order, SANDBOX_KEY);

        assertThat(RedsysSignature.verify(params, order, signature, SANDBOX_KEY)).isTrue();
    }

    @Test
    @DisplayName("round-trip con firma URL-safe (como la envía Redsys en la notificación)")
    void verify_accepts_url_safe_signature() {
        String order = "0003aa11bb22";
        String params = buildParams(order);
        String standard = RedsysSignature.sign(params, order, SANDBOX_KEY);
        // Redsys notifications carry the signature in URL-safe Base64.
        String urlSafe = standard.replace('+', '-').replace('/', '_');

        assertThat(RedsysSignature.verify(params, order, urlSafe, SANDBOX_KEY)).isTrue();
    }

    @Test
    @DisplayName("firma inválida (manipulada) → verify=false")
    void verify_rejects_tampered_signature() {
        String order = "0004cc33dd44";
        String params = buildParams(order);
        String signature = RedsysSignature.sign(params, order, SANDBOX_KEY);

        // Flip the first character of the (Base64) signature.
        char first = signature.charAt(0);
        String tampered = (first == 'A' ? 'B' : 'A') + signature.substring(1);

        assertThat(RedsysSignature.verify(params, order, tampered, SANDBOX_KEY)).isFalse();
    }

    @Test
    @DisplayName("parámetros manipulados con la firma original → verify=false")
    void verify_rejects_tampered_parameters() {
        String order = "0005ee55ff66";
        String params = buildParams(order);
        String signature = RedsysSignature.sign(params, order, SANDBOX_KEY);

        String tamperedParams = buildParams(order) // rebuild identical then mutate a byte
                .substring(0, buildParams(order).length() - 4) + "AAAA";

        assertThat(RedsysSignature.verify(tamperedParams, order, signature, SANDBOX_KEY)).isFalse();
    }

    @Test
    @DisplayName("clave de comercio distinta → verify=false")
    void verify_rejects_wrong_key() {
        String order = "0006112233ab";
        String params = buildParams(order);
        String signature = RedsysSignature.sign(params, order, SANDBOX_KEY);

        String otherKey = Base64.getEncoder()
                .encodeToString("0123456789abcdef01234567".getBytes(StandardCharsets.UTF_8));

        assertThat(RedsysSignature.verify(params, order, signature, otherKey)).isFalse();
    }
}
