package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.PagoHistorialResponse;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PagoQueryService} — own vs all payment history (pagos-redsys-online, group 5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PagoQueryService — historial de pagos")
class PagoQueryServiceTest {

    private static final long OWNER_ID = 1L;
    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock private PaymentJpaRepository paymentRepository;
    @Mock private ReservationJpaRepository reservationRepository;

    private PagoQueryService service;

    @BeforeEach
    void setUp() {
        service = new PagoQueryService(paymentRepository, reservationRepository);
    }

    private Reservation reservation() {
        return Reservation.builder()
                .id(RES_ID).ownerId(OWNER_ID).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB).build();
    }

    private Payment payment() {
        return Payment.pendingFor(RES_ID, new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("listForUser: pagos de reservas propias, con datos de reserva")
    void list_for_user() {
        when(reservationRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(reservation()));
        when(paymentRepository.findByReservationIdIn(any())).thenReturn(List.of(payment()));

        List<PagoHistorialResponse> result = service.listForUser(OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reservaId()).isEqualTo(RES_ID.toString());
        assertThat(result.get(0).ownerId()).isEqualTo(OWNER_ID);
        assertThat(result.get(0).amount()).isEqualByComparingTo("15.00");
        assertThat(result.get(0).reservationDate()).isNotNull();
    }

    @Test
    @DisplayName("listForUser: sin reservas → lista vacía")
    void list_for_user_empty() {
        when(reservationRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());

        assertThat(service.listForUser(OWNER_ID)).isEmpty();
    }

    @Test
    @DisplayName("listAll: todos los pagos (ADMIN)")
    void list_all() {
        when(paymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(payment()));
        when(reservationRepository.findAllById(any())).thenReturn(List.of(reservation()));

        List<PagoHistorialResponse> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reservaId()).isEqualTo(RES_ID.toString());
        assertThat(result.get(0).ownerId()).isEqualTo(OWNER_ID);
    }
}
