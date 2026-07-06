package com.padelpro.pagos.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.model.RedsysSignature;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

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

    /** The only signature version Redsys emits for this integration (BAJO-2). */
    private static final String SUPPORTED_SIGNATURE_VERSION = "HMAC_SHA256_V1";

    /**
     * Allowed shape of a {@code Ds_Order} (Redsys: 4–12 chars, accept up to 32 defensively). Anything
     * outside this alphabet is treated as attacker-controlled and never written verbatim to the audit
     * log (MEDIO-3, log-injection / stored-XSS defence).
     */
    private static final Pattern ORDER_PATTERN = Pattern.compile("^[0-9A-Za-z]{1,32}$");

    private final PaymentCommandPort paymentCommandPort;
    private final ReservationCommandPort reservationCommandPort;
    private final RedsysConfigService redsysConfigService;
    private final PagoAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProcesarWebhookService(PaymentCommandPort paymentCommandPort,
                                  ReservationCommandPort reservationCommandPort,
                                  RedsysConfigService redsysConfigService,
                                  PagoAuditRecorder auditRecorder,
                                  ObjectMapper objectMapper,
                                  ApplicationEventPublisher eventPublisher) {
        this.paymentCommandPort = paymentCommandPort;
        this.reservationCommandPort = reservationCommandPort;
        this.redsysConfigService = redsysConfigService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
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

        // 2. Reject any unsupported signature version outright — a valid Redsys notification is always
        //    HMAC_SHA256_V1 (BAJO-2). Anything else is audited as an invalid signature and dropped.
        if (!SUPPORTED_SIGNATURE_VERSION.equals(signatureVersion)) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE, null,
                    "orderId=" + safeOrder(order) + ", reason=unsupported-signature-version");
            return;
        }

        // 3. Verify signature BEFORE touching anything (RN-PAY-01, constant time inside verify()).
        String merchantKey = redsysConfigService.loadCredentials().merchantKey();
        boolean valid = order != null
                && RedsysSignature.verify(merchantParameters, order, signature, merchantKey);
        if (!valid) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE, null,
                    "orderId=" + safeOrder(order));
            return;
        }

        // 4. Locate the payment under a pessimistic write lock so concurrent notifications for the same
        //    order serialise (MEDIO-2): the second waits, re-reads PAID below, and skips reprocessing.
        Optional<Payment> found = paymentCommandPort.findByRedsysOrderIdForUpdate(order);
        if (found.isEmpty()) {
            auditRecorder.record(PagoAuditActions.PAYMENT_WEBHOOK_ORDER_NOT_FOUND, null,
                    "orderId=" + safeOrder(order));
            return;
        }
        Payment payment = found.get();

        // 5. Idempotent: an already-PAID payment is never reprocessed (RN-PAY-02). Under the lock
        //    above, a concurrent duplicate reaches this branch and no-ops.
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        // 6. Apply outcome by Ds_Response (0000..0099 = approved).
        Integer response = parseResponse(text(params, "Ds_Response"));
        if (response != null && response >= 0 && response <= 99) {
            payment.markPaid(text(params, "Ds_AuthorisationCode"), OffsetDateTime.now());
            Payment saved = paymentCommandPort.save(payment);
            auditRecorder.record(PagoAuditActions.PAYMENT_CONFIRMED, null,
                    "orderId=" + safeOrder(order) + ", reservationId=" + payment.getReservationId());

            // Notification trigger (change notificaciones-eventos-email, D1): receipt email to the
            // titular. Published inside the tx; delivered post-commit. Reference is the transaction id
            // (Ds_AuthorisationCode) — never card data (RN-PAY-03). Owner resolved via the reservation.
            reservationCommandPort.findById(saved.getReservationId()).ifPresent(reservation ->
                    eventPublisher.publishEvent(new PaymentPaidEmailEvent(
                            reservation.getId(), reservation.getOwnerId(), saved.getAmount(),
                            saved.getPaidAt(), saved.getTransactionId())));
        } else {
            payment.markFailed();
            paymentCommandPort.save(payment);
            auditRecorder.record(PagoAuditActions.PAYMENT_REJECTED, null,
                    "orderId=" + safeOrder(order) + ", dsResponse=" + response);
        }
    }

    /**
     * Sanitise a {@code Ds_Order} value before it is written to the audit log (MEDIO-3). Returns the
     * order unchanged only when it matches the strict alphanumeric shape; any attacker-controlled
     * payload (newlines, {@code <script>…}, over-length) collapses to {@code <invalid-format>} so it
     * can never inject log lines or be stored/rendered verbatim.
     */
    private String safeOrder(String order) {
        return (order != null && ORDER_PATTERN.matcher(order).matches()) ? order : "<invalid-format>";
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
