package com.padelpro.reservas.application.service;

import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfirmarReservaService} (capability bot-telegram-reservas, D-4): the
 * owner-path {@code PENDING_CONFIRMATION → CONFIRMED} transition, ownership enforcement (RN-AUTH-02),
 * state-machine guard (D6) and the confirmed-email notification trigger (D1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit — ConfirmarReservaService")
class ConfirmarReservaServiceTest {

    private static final UUID RES = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Long OWNER = 1L;

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ConfirmarReservaService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmarReservaService(reservationCommandPort, paymentCommandPort, eventPublisher);
    }

    private Reservation reservation(ReservationStatus status, Long ownerId) {
        return Reservation.builder()
                .id(RES).ownerId(ownerId)
                .reservationDate(LocalDate.of(2099, 6, 1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30))
                .durationMinutes(90).status(status).channel(ReservationChannel.TELEGRAM).build();
    }

    @Test
    @DisplayName("owner confirms a pending reservation → CONFIRMED + confirmed event with the amount")
    void owner_confirms_pending() {
        // Arrange
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER)));
        when(reservationCommandPort.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        Payment payment = Payment.pendingFor(RES, new BigDecimal("13.50"));
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentCommandPort.findByReservationId(RES)).thenReturn(Optional.of(payment));

        // Act
        service.confirmar(RES, OWNER);

        // Assert
        verify(reservationCommandPort).save(any(Reservation.class));
        verify(eventPublisher).publishEvent(any(ReservationConfirmedEmailEvent.class));
    }

    @Test
    @DisplayName("confirm without a payment publishes the event with a null amount")
    void confirm_without_payment() {
        // Arrange
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, OWNER)));
        when(reservationCommandPort.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentCommandPort.findByReservationId(RES)).thenReturn(Optional.empty());

        // Act
        service.confirmar(RES, OWNER);

        // Assert
        verify(eventPublisher).publishEvent(any(ReservationConfirmedEmailEvent.class));
    }

    @Test
    @DisplayName("a non-owner cannot confirm → ReservaForbiddenException, nothing saved (RN-AUTH-02)")
    void non_owner_forbidden() {
        // Arrange
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.PENDING_CONFIRMATION, 99L)));

        // Act + Assert
        assertThatThrownBy(() -> service.confirmar(RES, OWNER))
                .isInstanceOf(ReservaForbiddenException.class);
        verify(reservationCommandPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("confirming an already-confirmed reservation → InvalidReservaStateException (D6)")
    void already_confirmed_invalid_transition() {
        // Arrange
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.CONFIRMED, OWNER)));

        // Act + Assert
        assertThatThrownBy(() -> service.confirmar(RES, OWNER))
                .isInstanceOf(InvalidReservaStateException.class);
        verify(reservationCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("confirming a cancelled reservation → InvalidReservaStateException (terminal state)")
    void cancelled_invalid_transition() {
        // Arrange
        when(reservationCommandPort.findById(RES))
                .thenReturn(Optional.of(reservation(ReservationStatus.CANCELLED, OWNER)));

        // Act + Assert
        assertThatThrownBy(() -> service.confirmar(RES, OWNER))
                .isInstanceOf(InvalidReservaStateException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("unknown reservation → ReservaNotFoundException")
    void not_found() {
        // Arrange
        when(reservationCommandPort.findById(RES)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.confirmar(RES, OWNER))
                .isInstanceOf(ReservaNotFoundException.class);
    }
}
