package com.padelpro.pagos.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.model.RedsysSignature;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProcesarWebhookService} — signature-first, idempotent, always-200 processing
 * (pagos-redsys-online, D3). The real {@link RedsysSignature} produces/validates against the sandbox key.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcesarWebhookService — webhook Redsys (D3)")
class ProcesarWebhookServiceTest {

    private static final String SANDBOX_KEY = "sq7HjrUOBfKmC576ILgskD5srU870gJ7";
    private static final String ORDER = "0009AB12CD34";
    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private RedsysConfigService redsysConfigService;
    @Mock private PagoAuditRecorder auditRecorder;

    private ProcesarWebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ProcesarWebhookService(paymentCommandPort, redsysConfigService, auditRecorder,
                objectMapper);
    }

    private void credsAvailable() {
        when(redsysConfigService.loadCredentials())
                .thenReturn(new RedsysCredentials("999008881", SANDBOX_KEY, "1"));
    }

    /** Build a Base64 notification parameters string for the given fields. */
    private String params(String order, String dsResponse, String authCode) {
        StringBuilder json = new StringBuilder("{\"Ds_Order\":\"").append(order).append("\"");
        if (dsResponse != null) {
            json.append(",\"Ds_Response\":\"").append(dsResponse).append("\"");
        }
        if (authCode != null) {
            json.append(",\"Ds_AuthorisationCode\":\"").append(authCode).append("\"");
        }
        json.append(",\"Ds_Amount\":\"1500\",\"Ds_MerchantCode\":\"999008881\"}");
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String params) {
        return RedsysSignature.sign(params, ORDER, SANDBOX_KEY);
    }

    private Payment payment(PaymentStatus status) {
        Payment p = Payment.pendingFor(RES_ID, new BigDecimal("15.00"));
        p.markInProgress(ORDER, "https://tpv");
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("firma válida + aprobado (0000) → PAID + transaction_id + PAYMENT_CONFIRMED")
    void valid_approved_marks_paid() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER)).thenReturn(Optional.of(payment));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getTransactionId()).isEqualTo("ABC123");
        assertThat(saved.getPaidAt()).isNotNull();
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_CONFIRMED), isNull(), any());
    }

    @Test
    @DisplayName("firma válida + rechazo (0184) → FAILED + PAYMENT_REJECTED")
    void valid_rejected_marks_failed() {
        credsAvailable();
        String p = params(ORDER, "0184", null);
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER)).thenReturn(Optional.of(payment));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_REJECTED), isNull(), any());
    }

    @Test
    @DisplayName("firma inválida → sin cambio + PAYMENT_WEBHOOK_INVALID_SIGNATURE")
    void invalid_signature_no_change() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");

        service.procesar("HMAC_SHA256_V1", p, "not-a-valid-signature");

        verify(paymentCommandPort, never()).findByRedsysOrderIdForUpdate(any());
        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE), isNull(), any());
    }

    @Test
    @DisplayName("duplicado sobre pago ya PAID → sin reproceso (idempotente)")
    void duplicate_on_paid_is_noop() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder, never()).record(eq(PagoAuditActions.PAYMENT_CONFIRMED), any(), any());
    }

    @Test
    @DisplayName("order_id inexistente → PAYMENT_WEBHOOK_ORDER_NOT_FOUND, sin cambios")
    void unknown_order_audited() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER)).thenReturn(Optional.empty());

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_ORDER_NOT_FOUND), isNull(), any());
    }

    @Test
    @DisplayName("parámetros indescifrables → INVALID_SIGNATURE sin tocar el pago")
    void undecodable_params_audited() {
        lenient().when(redsysConfigService.loadCredentials())
                .thenReturn(new RedsysCredentials("999008881", SANDBOX_KEY, "1"));

        service.procesar("HMAC_SHA256_V1", "%%%not-base64%%%", "sig");

        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE), isNull(), any());
    }

    // =========================================================================
    // MEDIO-2 — idempotencia bajo carrera: bloqueo pesimista de la fila
    // =========================================================================

    @Test
    @DisplayName("MEDIO-2: el webhook localiza el pago con el finder que bloquea la fila (FOR UPDATE)")
    void webhook_locates_payment_with_locking_finder() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS)));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        // Debe usar el finder con lock pesimista, NUNCA la lectura sin lock (evita la carrera).
        verify(paymentCommandPort).findByRedsysOrderIdForUpdate(ORDER);
        verify(paymentCommandPort, never()).findByRedsysOrderId(any());
    }

    @Test
    @DisplayName("MEDIO-2: dos notificaciones del mismo webhook → un solo markPaid / un solo PAYMENT_CONFIRMED")
    void concurrent_duplicate_confirms_once() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        // Bajo el lock las dos notificaciones se serializan sobre la MISMA fila: la 1ª la deja PAID,
        // la 2ª re-lee ese estado PAID ya confirmado.
        Payment shared = payment(PaymentStatus.IN_PROGRESS);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER)).thenReturn(Optional.of(shared));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p)); // 1ª notificación
        service.procesar("HMAC_SHA256_V1", p, sign(p)); // 2ª notificación (duplicada)

        assertThat(shared.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentCommandPort, times(1)).save(any());
        verify(auditRecorder, times(1)).record(eq(PagoAuditActions.PAYMENT_CONFIRMED), isNull(), any());
    }
}
