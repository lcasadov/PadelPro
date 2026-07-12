package com.padelpro.pagos.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.model.RedsysSignature;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
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
    @Mock private com.padelpro.reservas.domain.port.out.ReservationCommandPort reservationCommandPort;
    @Mock private RedsysConfigService redsysConfigService;
    @Mock private PagoAuditRecorder auditRecorder;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private ProcesarWebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ProcesarWebhookService(paymentCommandPort, reservationCommandPort,
                redsysConfigService, auditRecorder, objectMapper, eventPublisher);
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

    private Reservation reservation() {
        return Reservation.builder()
                .id(RES_ID).ownerId(1L).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB).build();
    }

    @Test
    @DisplayName("firma válida + aprobado (0000) → PAID + transaction_id + PAYMENT_CONFIRMED")
    void valid_approved_marks_paid() {
        credsAvailable();
        String p = params(ORDER, "0000", "ABC123");
        Payment payment = payment(PaymentStatus.IN_PROGRESS);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER)).thenReturn(Optional.of(payment));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Owner resolved via the reservation so the receipt-email event can be published.
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation()));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getTransactionId()).isEqualTo("ABC123");
        assertThat(saved.getPaidAt()).isNotNull();
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_CONFIRMED), isNull(), any());
        // Notification trigger (change notificaciones-eventos-email, D1): receipt email to the titular.
        verify(eventPublisher).publishEvent(any(PaymentPaidEmailEvent.class));
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

    private String rawParams(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Ds_Response ausente → parseResponse null → FAILED + PAYMENT_REJECTED")
    void missing_ds_response_marks_failed() {
        credsAvailable();
        String p = params(ORDER, null, null); // no Ds_Response field at all
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS)));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_REJECTED), isNull(), any());
    }

    @Test
    @DisplayName("Ds_Response no numérico → parseResponse null → FAILED")
    void non_numeric_ds_response_marks_failed() {
        credsAvailable();
        String p = params(ORDER, "ABCD", null);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS)));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("Ds_Order ausente → order null → tratado como firma inválida, sin tocar el pago")
    void missing_order_treated_as_invalid_signature() {
        credsAvailable();
        String p = rawParams("{\"Ds_Response\":\"0000\",\"Ds_Amount\":\"1500\"}");

        service.procesar("HMAC_SHA256_V1", p, "any-signature");

        verify(paymentCommandPort, never()).findByRedsysOrderIdForUpdate(any());
        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE), isNull(), any());
    }

    @Test
    @DisplayName("lookup de campos case-insensitive: ds_order/ds_response en minúscula → PAID")
    void case_insensitive_field_lookup() {
        credsAvailable();
        // Redsys field names in a non-canonical case exercise the case-insensitive fallback in text().
        String json = "{\"ds_order\":\"" + ORDER + "\",\"ds_response\":\"0000\","
                + "\"Ds_AuthorisationCode\":\"ABC123\",\"Ds_Amount\":\"1500\"}";
        String p = rawParams(json);
        when(paymentCommandPort.findByRedsysOrderIdForUpdate(ORDER))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS)));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // No owner resolvable → the receipt-email event is simply not published (ifPresent).
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.empty());

        service.procesar("HMAC_SHA256_V1", p, sign(p));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
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

    // =========================================================================
    // MEDIO-3 — sanitizar Ds_Order en auditoría (log-injection / XSS)
    // =========================================================================

    @Test
    @DisplayName("MEDIO-3: Ds_Order malicioso con firma inválida → audita <invalid-format>, no el payload")
    void malicious_order_sanitised_in_audit() {
        credsAvailable();
        String malicious = "<script>alert(1)</script>";
        String p = params(malicious, "0000", "ABC123");

        service.procesar("HMAC_SHA256_V1", p, "not-a-valid-signature");

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE),
                isNull(), details.capture());
        assertThat(details.getValue()).contains("<invalid-format>");
        assertThat(details.getValue()).doesNotContain("<script>");
        assertThat(details.getValue()).doesNotContain("alert");
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("MEDIO-3: Ds_Order con salto de línea → audita <invalid-format> (no inyecta líneas de log)")
    void newline_order_sanitised_in_audit() {
        credsAvailable();
        // Salto de línea escapado dentro del JSON de parámetros → valor real con '\n'.
        String withNewline = "0009\\nINJECTED";
        String p = params(withNewline, "0000", "ABC123");

        service.procesar("HMAC_SHA256_V1", p, "not-a-valid-signature");

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE),
                isNull(), details.capture());
        assertThat(details.getValue()).contains("<invalid-format>");
        assertThat(details.getValue()).doesNotContain("INJECTED");
    }

    // =========================================================================
    // BAJO-2 — validar Ds_SignatureVersion
    // =========================================================================

    @Test
    @DisplayName("BAJO-2: signatureVersion != HMAC_SHA256_V1 → INVALID_SIGNATURE, sin tocar el pago")
    void unsupported_signature_version_rejected() {
        String p = params(ORDER, "0000", "ABC123");

        // Firma correcta pero versión no soportada: se rechaza igualmente.
        service.procesar("HMAC_SHA1_V1", p, sign(p));

        verify(paymentCommandPort, never()).findByRedsysOrderIdForUpdate(any());
        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_WEBHOOK_INVALID_SIGNATURE), isNull(), any());
    }
}
