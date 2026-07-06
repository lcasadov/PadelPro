package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.EfectivoPagoResponse;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.exception.PagoNotFoundException;
import com.padelpro.pagos.domain.exception.PagoUnprocessableException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistrarPagoEfectivoService} — ADMIN cash registration (pagos-redsys-online,
 * group 5). USER → 403 is enforced by the security layer and covered at the controller/IT level.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrarPagoEfectivoService — efectivo ADMIN")
class RegistrarPagoEfectivoServiceTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long ADMIN_ID = 99L;

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private PagoAuditRecorder auditRecorder;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private RegistrarPagoEfectivoService service;

    @BeforeEach
    void setUp() {
        service = new RegistrarPagoEfectivoService(reservationCommandPort, paymentCommandPort,
                auditRecorder, eventPublisher);
    }

    private Reservation reservation() {
        return Reservation.builder()
                .id(RES_ID).ownerId(1L).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB).build();
    }

    private Payment payment(PaymentStatus status) {
        Payment p = Payment.pendingFor(RES_ID, new BigDecimal("15.00"));
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("ADMIN registra efectivo → PAID/CASH/registered_by + PAYMENT_CASH_REGISTERED")
    void admin_registers_cash() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation()));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        when(paymentCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EfectivoPagoResponse response = service.registrar(RES_ID, ADMIN_ID);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.method()).isEqualTo("CASH");
        assertThat(response.registeredById()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentCommandPort).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getRegisteredById()).isEqualTo(ADMIN_ID);
        assertThat(saved.getPaidAt()).isNotNull();
        verify(auditRecorder).record(eq(PagoAuditActions.PAYMENT_CASH_REGISTERED), eq(ADMIN_ID), any());
    }

    @Test
    @DisplayName("reserva inexistente → 404")
    void reservation_not_found() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(RES_ID, ADMIN_ID))
                .isInstanceOf(PagoNotFoundException.class);
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva ya PAID → 422 ALREADY_PAID")
    void already_paid() {
        when(reservationCommandPort.findById(RES_ID)).thenReturn(Optional.of(reservation()));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        assertThatThrownBy(() -> service.registrar(RES_ID, ADMIN_ID))
                .isInstanceOf(PagoUnprocessableException.class)
                .extracting(e -> ((PagoUnprocessableException) e).getCode())
                .isEqualTo("ALREADY_PAID");
        verify(paymentCommandPort, never()).save(any());
    }
}
