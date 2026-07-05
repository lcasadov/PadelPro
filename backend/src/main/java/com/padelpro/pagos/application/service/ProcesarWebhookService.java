package com.padelpro.pagos.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.model.RedsysSignature;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Process a Redsys notification webhook (pagos-redsys-online, D3).
 *
 * <p>Order of operations (RN-PAY-01/02/03):
 * <ol>
 *   <li>Decode {@code Ds_MerchantParameters} and extract {@code Ds_Order} — no state change yet.</li>
 *   <li>Verify the HMAC signature in constant time. Invalid → audit
 *       {@code PAYMENT_WEBHOOK_INVALID_SIGNATURE} and stop (no payment touched).</li>
 *   <li>Locate the payment by {@code redsys_order_id}; unknown → audit and stop.</li>
 *   <li>Already {@code PAID} → idempotent no-op.</li>
 *   <li>{@code Ds_Response} in {@code 0000..0099} → PAID + transaction id + {@code PAYMENT_CONFIRMED};
 *       otherwise FAILED + {@code PAYMENT_REJECTED}.</li>
 * </ol>
 *
 * <p>The caller always responds HTTP 200. The raw body and card data are never persisted or logged.
 */
public class ProcesarWebhookService {

    private final PaymentCommandPort paymentCommandPort;
    private final RedsysConfigService redsysConfigService;
    private final PagoAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ProcesarWebhookService(PaymentCommandPort paymentCommandPort,
                                  RedsysConfigService redsysConfigService,
                                  PagoAuditRecorder auditRecorder,
                                  ObjectMapper objectMapper) {
        this.paymentCommandPort = paymentCommandPort;
        this.redsysConfigService = redsysConfigService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void procesar(String signatureVersion, String merchantParameters, String signature) {
        // 1. Decode parameters (no state change). A malformed body is treated as an invalid signature.
        JsonNode params = decodeParameters(merchantParameters);
        if (params == null) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE, null,
                    "reason=undecodable-parameters");
            return;
        }
        String order = text(params, "Ds_Order");

        // 2. Verify signature BEFORE touching anything (RN-PAY-01, constant time inside verify()).
        String merchantKey = redsysConfigService.loadCredentials().merchantKey();
        boolean valid = order != null
                && RedsysSignature.verify(merchantParameters, order, signature, merchantKey);
        if (!valid) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE, null,
                    "orderId=" + order);
            return;
        }

        // 3. Locate the payment (idempotency anchor, RN-PAY-02).
        Optional<Payment> found = paymentCommandPort.findByRedsysOrderId(order);
        if (found.isEmpty()) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_ORDER_NOT_FOUND, null,
                    "orderId=" + order);
            return;
        }
        Payment payment = found.get();

        // 4. Idempotent: an already-PAID payment is never reprocessed (RN-PAY-02).
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        // 5. Apply outcome by Ds_Response (0000..0099 = approved).
        Integer response = parseResponse(text(params, "Ds_Response"));
        if (response != null && response >= 0 && response <= 99) {
            payment.markPaid(text(params, "Ds_AuthorisationCode"), OffsetDateTime.now());
            paymentCommandPort.save(payment);
            auditRecorder.record(PagoAuditActions.PAYMENT_CONFIRMED, null,
                    "orderId=" + order + ", reservationId=" + payment.getReservationId());
        } else {
            payment.markFailed();
            paymentCommandPort.save(payment);
            auditRecorder.record(PagoAuditActions.PAYMENT_REJECTED, null,
                    "orderId=" + order + ", dsResponse=" + response);
        }
    }

    private JsonNode decodeParameters(String merchantParameters) {
        if (merchantParameters == null || merchantParameters.isBlank()) {
            return null;
        }
        try {
            // Redsys uses URL-safe Base64 in notifications; normalise so both variants decode.
            String normalised = merchantParameters.replace('+', '-').replace('/', '_');
            byte[] json = Base64.getUrlDecoder().decode(normalised);
            return objectMapper.readTree(new String(json, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** Case-insensitive field read (Redsys uses {@code Ds_Order}; be lenient). */
    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null) {
            java.util.Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String n = names.next();
                if (n.equalsIgnoreCase(field)) {
                    v = node.get(n);
                    break;
                }
            }
        }
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private Integer parseResponse(String dsResponse) {
        if (dsResponse == null || dsResponse.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(dsResponse.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
