package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.SimularPagoRequest;
import com.padelpro.pagos.application.dto.SimularPagoResponse;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.exception.PagoConflictException;
import com.padelpro.pagos.domain.exception.PagoForbiddenException;
import com.padelpro.pagos.domain.exception.PagoValidationException;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentMethod;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimularPagoService} — provisional online payment simulator
 * (change pagos-simulador-gestion, D2). Ports are mocked; the randomness source is injected so
 * non-magic outcomes are deterministic. No card data ever reaches persistence or the audit log
 * (RN-RGPD-04).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimularPagoService — simulador de pago online (D2)")
class SimularPagoServiceTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;

    /** A valid future expiry (always ahead of the current month) so format checks pass. */
    private static final String FUTURE_EXPIRY = validFutureExpiry();
    private static final String VALID_CVC = "123";

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private PagoAuditRecorder auditRecorder;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private RandomGenerator random;

    private SimularPagoService service;

    @BeforeEach
    void setUp() {
        service = new SimularPagoService(reservationCommandPort, paymentCommandPort, auditRecorder,
                eventPublisher, random);
    }

    private static String validFutureExpiry() {
        YearMonth next = YearMonth.now().plusMonths(2);
        return String.format("%02d/%02d", next.getMonthValue(), next.getYear() % 100);
    }

    private Reservation reservation(long ownerId) {
        return Reservation.builder()
                .id(RES_ID).ownerId(ownerId).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB).build();
    }

    private Payment payment(PaymentStatus status) {
        Payment p = Payment.pendingFor(RES_ID, new BigDecimal("15.00"));
        p.setStatus(status);
        return p;
    }

    private SimularPagoRequest request(String cardNumber) {
        return new SimularPagoRequest(RES_ID, cardNumber, FUTURE_EXPIRY, VALID_CVC);
    }

    private void stubOwnedPendingPayment() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation(OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
    }

    @Test
    @DisplayName("tarjeta mágica de aprobación → APPROVED + PAID/SIMULADO + evento recibo")
    void magic_approve_card() {
        stubOwnedPendingPayment();
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SimularPagoResponse response = service.simular(request("4111 1111 1111 1111"), OWNER_ID);

        assertThat(response.resultado()).isEqualTo("APPROVED");
        assertThat(response.motivo()).isNull();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.SIMULADO);
        assertThat(saved.getPaidAt()).isNotNull();
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_SIMULATED_APPROVED), eq(OWNER_ID), any());
        verify(eventPublisher).publishEvent(any(PaymentPaidEmailEvent.class));
    }

    @Test
    @DisplayName("tarjeta mágica de rechazo → DECLINED + pago sigue PENDING + sin evento")
    void magic_decline_card() {
        stubOwnedPendingPayment();

        SimularPagoResponse response = service.simular(request("4000 0000 0000 0002"), OWNER_ID);

        assertThat(response.resultado()).isEqualTo("DECLINED");
        assertThat(response.motivo()).isNotBlank();
        verify(paymentCommandPort, never()).save(any());
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_SIMULATED_DECLINED), eq(OWNER_ID), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("tarjeta normal con RNG < 85 → APPROVED (rama determinista)")
    void random_card_below_threshold_approves() {
        stubOwnedPendingPayment();
        when(random.nextInt(100)).thenReturn(84); // 84 < 85 → approve
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SimularPagoResponse response = service.simular(request("5555 5555 5555 4444"), OWNER_ID);

        assertThat(response.resultado()).isEqualTo("APPROVED");
        verify(paymentCommandPort).save(any());
    }

    @Test
    @DisplayName("tarjeta normal con RNG >= 85 → DECLINED (rama determinista)")
    void random_card_at_threshold_declines() {
        stubOwnedPendingPayment();
        when(random.nextInt(100)).thenReturn(85); // 85 not < 85 → decline

        SimularPagoResponse response = service.simular(request("5555 5555 5555 4444"), OWNER_ID);

        assertThat(response.resultado()).isEqualTo("DECLINED");
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("misma semilla → mismo resultado (aleatoriedad reproducible)")
    void seeded_random_is_reproducible() {
        long seed = 42L;
        boolean expectedApproved = new Random(seed).nextInt(100) < SimularPagoService.APPROVE_THRESHOLD;

        SimularPagoService seededService = new SimularPagoService(reservationCommandPort,
                paymentCommandPort, auditRecorder, eventPublisher, new Random(seed));
        stubOwnedPendingPayment();
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SimularPagoResponse response = seededService.simular(request("6011 0009 9013 9424"), OWNER_ID);

        assertThat(response.resultado())
                .isEqualTo(expectedApproved ? "APPROVED" : "DECLINED");
    }

    @Test
    @DisplayName("número de tarjeta con longitud inválida → 400 VALIDATION_ERROR")
    void invalid_card_length() {
        assertThatThrownBy(() -> service.simular(
                new SimularPagoRequest(RES_ID, "4111", FUTURE_EXPIRY, VALID_CVC), OWNER_ID))
                .isInstanceOf(PagoValidationException.class);
        verify(reservationCommandPort, never()).findById(any());
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("caducidad no futura → 400 VALIDATION_ERROR")
    void expired_card() {
        assertThatThrownBy(() -> service.simular(
                new SimularPagoRequest(RES_ID, "4242424242424242", "01/20", VALID_CVC), OWNER_ID))
                .isInstanceOf(PagoValidationException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("CVC no numérico de 3 dígitos → 400 VALIDATION_ERROR")
    void invalid_cvc() {
        assertThatThrownBy(() -> service.simular(
                new SimularPagoRequest(RES_ID, "4242424242424242", FUTURE_EXPIRY, "12"), OWNER_ID))
                .isInstanceOf(PagoValidationException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("no-dueño → 403 y el pago no cambia")
    void non_owner_forbidden() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation(OWNER_ID)));

        assertThatThrownBy(() -> service.simular(request("4111111111111111"), OTHER_ID))
                .isInstanceOf(PagoForbiddenException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva inexistente → 403 (no filtra existencia)")
    void reservation_not_found_forbidden() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simular(request("4111111111111111"), OWNER_ID))
                .isInstanceOf(PagoForbiddenException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva ya pagada → 409 idempotente (no re-cobra)")
    void already_paid_conflict() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation(OWNER_ID)));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        assertThatThrownBy(() -> service.simular(request("4111111111111111"), OWNER_ID))
                .isInstanceOf(PagoConflictException.class);
        verify(paymentCommandPort, never()).save(any());
    }
}
