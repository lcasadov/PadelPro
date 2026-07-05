package com.padelpro.pagos.application.service;

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
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IniciarPagoService} — guards + signed-form generation (pagos-redsys-online, D5).
 * Ports mocked; the real {@link RedsysSignature} runs against the sandbox key.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IniciarPagoService — iniciar pago Redsys (D5)")
class IniciarPagoServiceTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final String SANDBOX_KEY = "sq7HjrUOBfKmC576ILgskD5srU870gJ7";
    private static final String FIXED_ORDER = "0001AB23CD45";

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private RedsysConfigService redsysConfigService;
    @Mock private RedsysOrderIdGenerator orderIdGenerator;
    @Mock private PagoAuditRecorder auditRecorder;

    private IniciarPagoService service;

    @BeforeEach
    void setUp() {
        RedsysProperties props = new RedsysProperties(
                "https://sis-t.redsys.es:25443/sis/realizarPago",
                "https://example.test/api/pagos/webhook",
                "https://example.test/pago/confirmado",
                "https://example.test/pago/confirmado",
                "978");
        service = new IniciarPagoService(reservationCommandPort, paymentCommandPort, redsysConfigService,
                props, orderIdGenerator, auditRecorder, new ObjectMapper());
    }

    private Reservation reservation(ReservationStatus status, long ownerId) {
        return Reservation.builder()
                .id(RES_ID).ownerId(ownerId).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(status).channel(ReservationChannel.WEB).build();
    }

    private Payment payment(PaymentStatus status, String amount) {
        Payment p = Payment.pendingFor(RES_ID, new BigDecimal(amount));
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("owner OK → IN_PROGRESS + form firmado + PAYMENT_INITIATED")
    void owner_initiates_ok() {
        when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING, "15.00")));
        when(redsysConfigService.loadCredentials())
                .thenReturn(new RedsysCredentials("999008881", SANDBOX_KEY, "1"));
        when(orderIdGenerator.generate()).thenReturn(FIXED_ORDER);
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IniciarPagoResponse response = service.iniciar(RES_ID, OWNER_ID);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.redsysOrderId()).isEqualTo(FIXED_ORDER);
        assertThat(response.redsysUrl()).contains("sis-t.redsys.es");
        assertThat(response.dsSignatureVersion()).isEqualTo("HMAC_SHA256_V1");
        assertThat(response.amount()).isEqualByComparingTo("15.00");

        // The signature verifies against the sandbox key (round-trip through the real signer).
        assertThat(RedsysSignature.verify(
                response.dsMerchantParameters(), FIXED_ORDER, response.dsSignature(), SANDBOX_KEY)).isTrue();

        // Amount is sent to Redsys in céntimos (RN-RES-03), never from the client.
        String json = new String(Base64.getDecoder().decode(response.dsMerchantParameters()),
                StandardCharsets.UTF_8);
        assertThat(json).contains("\"DS_MERCHANT_AMOUNT\":\"1500\"");
        assertThat(json).contains("\"DS_MERCHANT_TERMINAL\":\"1\"");
        assertThat(json).contains("\"DS_MERCHANT_MERCHANTURL\":\"https://example.test/api/pagos/webhook\"");

        verify(paymentCommandPort).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_INITIATED), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("no-owner → 403 y el pago no cambia")
    void non_owner_forbidden() {
        when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER_ID)));

        assertThatThrownBy(() -> service.iniciar(RES_ID, OTHER_ID))
                .isInstanceOf(PagoForbiddenException.class);
        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("reserva inexistente → 404")
    void reservation_not_found() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.iniciar(RES_ID, OWNER_ID))
                .isInstanceOf(PagoNotFoundException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva cancelada → 422 RESERVA_CANCELLED")
    void reservation_cancelled() {
        when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.CANCELLED, OWNER_ID)));

        assertThatThrownBy(() -> service.iniciar(RES_ID, OWNER_ID))
                .isInstanceOf(PagoUnprocessableException.class)
                .extracting(e -> ((PagoUnprocessableException) e).getCode())
                .isEqualTo("RESERVA_CANCELLED");
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("pago ya IN_PROGRESS → 409")
    void payment_already_in_progress() {
        when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS, "15.00")));

        assertThatThrownBy(() -> service.iniciar(RES_ID, OWNER_ID))
                .isInstanceOf(PagoConflictException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("pago ya PAID → 409")
    void payment_already_paid() {
        when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.CONFIRMED, OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID, "15.00")));

        assertThatThrownBy(() -> service.iniciar(RES_ID, OWNER_ID))
                .isInstanceOf(PagoConflictException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("el importe se toma del backend en céntimos (no del cliente)")
    void amount_is_backend_frozen_in_cents() {
        lenient().when(reservationCommandPort.findById(RES_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING, "22.50")));
        when(redsysConfigService.loadCredentials())
                .thenReturn(new RedsysCredentials("999008881", SANDBOX_KEY, "1"));
        when(orderIdGenerator.generate()).thenReturn(FIXED_ORDER);
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IniciarPagoResponse response = service.iniciar(RES_ID, OWNER_ID);

        String json = new String(Base64.getDecoder().decode(response.dsMerchantParameters()),
                StandardCharsets.UTF_8);
        assertThat(json).contains("\"DS_MERCHANT_AMOUNT\":\"2250\"");
    }
}
