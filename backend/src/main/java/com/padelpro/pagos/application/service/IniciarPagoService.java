package com.padelpro.pagos.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.pagos.application.dto.IniciarPagoResponse;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.exception.PagoConflictException;
import com.padelpro.pagos.domain.exception.PagoForbiddenException;
import com.padelpro.pagos.domain.exception.PagoNotFoundException;
import com.padelpro.pagos.domain.exception.PagoUnprocessableException;
import com.padelpro.pagos.domain.model.RedsysSignature;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Start an online Redsys payment for a reservation (pagos-redsys-online, D5).
 *
 * <p>Guards: owner only (403), reservation exists (404), not cancelled (422), payment still PENDING
 * (409 if IN_PROGRESS/PAID). The charged amount is always the backend-frozen {@code payments.amount}
 * in céntimos (RN-RES-03) — any client-sent amount is ignored. On success the payment moves to
 * IN_PROGRESS with a unique {@code redsys_order_id} and the persisted {@code payment_url}, and
 * {@code PAYMENT_INITIATED} is audited.
 */
public class IniciarPagoService {

    private static final String SIGNATURE_VERSION = "HMAC_SHA256_V1";
    private static final String TRANSACTION_TYPE = "0"; // authorization

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final RedsysConfigService redsysConfigService;
    private final RedsysProperties redsysProperties;
    private final RedsysOrderIdGenerator orderIdGenerator;
    private final PagoAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public IniciarPagoService(ReservationCommandPort reservationCommandPort,
                              PaymentCommandPort paymentCommandPort,
                              RedsysConfigService redsysConfigService,
                              RedsysProperties redsysProperties,
                              RedsysOrderIdGenerator orderIdGenerator,
                              PagoAuditRecorder auditRecorder,
                              ObjectMapper objectMapper) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.redsysConfigService = redsysConfigService;
        this.redsysProperties = redsysProperties;
        this.orderIdGenerator = orderIdGenerator;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IniciarPagoResponse iniciar(UUID reservaId, Long currentUserId) {
        Reservation reservation = reservationCommandPort.findById(reservaId)
                .orElseThrow(() -> new PagoNotFoundException("Reserva no encontrada: " + reservaId));

        if (!reservation.getOwnerId().equals(currentUserId)) {
            throw new PagoForbiddenException("Solo el titular de la reserva puede iniciar el pago");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new PagoUnprocessableException("RESERVA_CANCELLED",
                    "La reserva está cancelada y no admite pagos");
        }

        Payment payment = paymentCommandPort.findByReservationId(reservaId)
                .orElseThrow(() -> new PagoNotFoundException(
                        "No existe un pago para la reserva: " + reservaId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            if (payment.getStatus() == PaymentStatus.PAID) {
                throw new PagoConflictException("La reserva ya está pagada");
            }
            throw new PagoConflictException("Ya hay un pago en curso para esta reserva");
        }

        RedsysCredentials credentials = redsysConfigService.loadCredentials();

        // RN-RES-03 — amount is the backend-frozen euro amount; convert to céntimos for Redsys.
        String amountCents = toCents(payment.getAmount());
        String order = orderIdGenerator.generate();

        String merchantParameters = buildMerchantParameters(order, amountCents, credentials);
        String signature = RedsysSignature.sign(merchantParameters, order, credentials.merchantKey());

        payment.markInProgress(order, redsysProperties.tpvUrl());
        Payment saved = paymentCommandPort.save(payment);

        auditRecorder.record(PagoAuditActions.PAYMENT_INITIATED, currentUserId,
                "reservationId=" + reservaId + ", orderId=" + order);

        return new IniciarPagoResponse(
                saved.getId() != null ? saved.getId().toString() : null,
                order,
                redsysProperties.tpvUrl(),
                payment.getAmount(),
                saved.getStatus().name(),
                SIGNATURE_VERSION,
                merchantParameters,
                signature);
    }

    /** Euros → céntimos string (e.g. {@code 15.00} → {@code "1500"}). */
    private String toCents(BigDecimal euros) {
        return euros.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact().toString();
    }

    private String buildMerchantParameters(String order, String amountCents, RedsysCredentials creds) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("DS_MERCHANT_AMOUNT", amountCents);
        params.put("DS_MERCHANT_ORDER", order);
        params.put("DS_MERCHANT_MERCHANTCODE", creds.merchantCode());
        params.put("DS_MERCHANT_CURRENCY", redsysProperties.currency());
        params.put("DS_MERCHANT_TRANSACTIONTYPE", TRANSACTION_TYPE);
        params.put("DS_MERCHANT_TERMINAL", creds.terminal());
        params.put("DS_MERCHANT_MERCHANTURL", redsysProperties.merchantUrl());
        params.put("DS_MERCHANT_URLOK", redsysProperties.urlOk());
        params.put("DS_MERCHANT_URLKO", redsysProperties.urlKo());
        try {
            String json = objectMapper.writeValueAsString(params);
            return Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Redsys merchant parameters", e);
        }
    }
}
