package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.notificaciones.domain.event.ReservationCancelledEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage unit tests for {@link AdminReservaService} (change backend-branch-coverage, D1/D2):
 * the ADMIN state-machine driver (D6), the refund-on-cancel guard (D5), availability invalidation and
 * the CONFIRMED/CANCELLED notification triggers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit — AdminReservaService")
class AdminReservaServiceTest {

    private static final UUID RES = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private DisponibilidadCacheInvalidator cacheInvalidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminReservaService service;

    @BeforeEach
    void setUp() {
        service = new AdminReservaService(reservationCommandPort, paymentCommandPort,
                cacheInvalidator, eventPublisher);
    }

    private Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .id(RES).ownerId(1L)
                .reservationDate(LocalDate.of(2099, 6, 1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(status).channel(ReservationChannel.WEB).build();
    }

    private Payment payment(PaymentStatus status) {
        Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
        p.setStatus(status);
        return p;
    }

    private void stubReservation(ReservationStatus status) {
        when(reservationCommandPort.findById(RES)).thenReturn(Optional.of(reservation(status)));
        when(reservationCommandPort.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // CONFIRMED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PENDING → CONFIRMED with a payment publishes the confirmed event carrying the amount")
    void confirm_with_payment_publishes_amount() {
        stubReservation(ReservationStatus.PENDING_CONFIRMATION);
        when(paymentCommandPort.findByReservationId(RES))
                .thenReturn(Optional.of(payment(PaymentStatus.IN_PROGRESS)));

        ReservaResponse response = service.cambiarEstado(RES, "confirmed");

        assertThat(response.status()).isEqualTo("CONFIRMED");
        verify(eventPublisher).publishEvent(any(ReservationConfirmedEmailEvent.class));
        // CONFIRMED does not change availability → no cache invalidation, and the payment is untouched
        verify(cacheInvalidator, never()).invalidate(any());
        verify(paymentCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("PENDING → CONFIRMED without a payment publishes the confirmed event with a null amount")
    void confirm_without_payment_null_amount() {
        stubReservation(ReservationStatus.PENDING_CONFIRMATION);
        when(paymentCommandPort.findByReservationId(RES)).thenReturn(Optional.empty());

        service.cambiarEstado(RES, "CONFIRMED");

        verify(eventPublisher).publishEvent(any(ReservationConfirmedEmailEvent.class));
    }

    // -------------------------------------------------------------------------
    // CANCELLED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CANCELLED with a PAID payment refunds it, invalidates the cache and notifies")
    void cancel_refunds_paid_payment() {
        stubReservation(ReservationStatus.CONFIRMED);
        when(paymentCommandPort.findByReservationId(RES))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID)));
        when(paymentCommandPort.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cambiarEstado(RES, "CANCELLED");

        // PAID → refunded and persisted
        verify(paymentCommandPort).save(any(Payment.class));
        verify(cacheInvalidator).invalidate(any());
        verify(eventPublisher).publishEvent(any(ReservationCancelledEmailEvent.class));
    }

    @Test
    @DisplayName("CANCELLED with a non-paid payment does not refund but still invalidates + notifies")
    void cancel_non_paid_payment_no_refund() {
        stubReservation(ReservationStatus.CONFIRMED);
        when(paymentCommandPort.findByReservationId(RES))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        service.cambiarEstado(RES, "CANCELLED");

        verify(paymentCommandPort, never()).save(any());
        verify(cacheInvalidator).invalidate(any());
        verify(eventPublisher).publishEvent(any(ReservationCancelledEmailEvent.class));
    }

    @Test
    @DisplayName("CANCELLED with no payment at all skips the refund guard entirely")
    void cancel_without_payment() {
        stubReservation(ReservationStatus.PENDING_CONFIRMATION);
        when(paymentCommandPort.findByReservationId(RES)).thenReturn(Optional.empty());

        service.cambiarEstado(RES, "CANCELLED");

        verify(paymentCommandPort, never()).save(any());
        verify(cacheInvalidator).invalidate(any());
        verify(eventPublisher).publishEvent(any(ReservationCancelledEmailEvent.class));
    }

    // -------------------------------------------------------------------------
    // COMPLETED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CONFIRMED → COMPLETED invalidates the cache but publishes no email event")
    void complete_invalidates_no_event() {
        stubReservation(ReservationStatus.CONFIRMED);
        when(paymentCommandPort.findByReservationId(RES)).thenReturn(Optional.empty());

        service.cambiarEstado(RES, "COMPLETED");

        verify(cacheInvalidator).invalidate(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Error paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("invalid transition (PENDING → COMPLETED) → 422, nothing saved")
    void invalid_transition() {
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION)));

        assertThatThrownBy(() -> service.cambiarEstado(RES, "COMPLETED"))
                .isInstanceOf(InvalidReservaStateException.class);
        verify(reservationCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("unknown reservation → ReservaNotFoundException")
    void not_found() {
        when(reservationCommandPort.findById(RES)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(RES, "CONFIRMED"))
                .isInstanceOf(ReservaNotFoundException.class);
    }

    @Test
    @DisplayName("null status → ValidationException (obligatorio)")
    void null_status() {
        assertThatThrownBy(() -> service.cambiarEstado(RES, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    @DisplayName("blank status → ValidationException")
    void blank_status() {
        assertThatThrownBy(() -> service.cambiarEstado(RES, "   "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("unrecognised status token → ValidationException (not a reservation state)")
    void invalid_status_token() {
        assertThatThrownBy(() -> service.cambiarEstado(RES, "BOGUS"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no es un estado");
    }
}
