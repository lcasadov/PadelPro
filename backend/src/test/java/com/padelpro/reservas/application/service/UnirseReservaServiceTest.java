package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.UnirseResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ParticipacionDuplicadaException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.ParticipantCommandPort;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UnirseReservaService} — join validations and slot assignment (capability
 * partidas, D2). Ports mocked; no database. The atomic last-seat race is covered by the concurrency IT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnirseReservaService — unirse a una partida")
class UnirseReservaServiceTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long OWNER_ID = 1L;
    private static final long JOINER_ID = 2L;

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private ParticipantCommandPort participantCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private SystemConfigRepositoryPort systemConfigRepositoryPort;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private DisponibilidadCacheInvalidator cacheInvalidator;

    private UnirseReservaService service;

    @BeforeEach
    void setUp() {
        service = new UnirseReservaService(reservationCommandPort, participantCommandPort,
                paymentCommandPort, systemConfigRepositoryPort, userRepositoryPort, cacheInvalidator);
    }

    private void configWithMax(int max) {
        SystemConfig config = SystemConfig.builder()
                .id(1L).clubName("Club").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH).maxParticipantsPerPista(max)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        lenient().when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.of(config));
    }

    private Reservation reservationWith(ReservationStatus status, Participant... participants) {
        List<Participant> list = new ArrayList<>(List.of(participants));
        return Reservation.builder()
                .id(RES_ID).ownerId(OWNER_ID).reservationDate(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(status).channel(ReservationChannel.WEB)
                .participants(list).build();
    }

    @Test
    @DisplayName("reserva inexistente → 404 ReservaNotFoundException")
    void should_throw_not_found_when_reservation_absent() {
        when(reservationCommandPort.findByIdForUpdate(RES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unirse(RES_ID, JOINER_ID))
                .isInstanceOf(ReservaNotFoundException.class);
        verify(participantCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva CANCELLED → 422 RESERVA_NOT_JOINABLE")
    void should_reject_when_reservation_cancelled() {
        Reservation r = reservationWith(ReservationStatus.CANCELLED,
                Participant.owner(OWNER_ID, ReservationChannel.WEB));
        when(reservationCommandPort.findByIdForUpdate(RES_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.unirse(RES_ID, JOINER_ID))
                .isInstanceOf(InvalidReservaStateException.class)
                .extracting(e -> ((InvalidReservaStateException) e).getCode())
                .isEqualTo("RESERVA_NOT_JOINABLE");
        verify(participantCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("usuario ya participante → 409 ParticipacionDuplicadaException")
    void should_reject_when_already_participant() {
        Reservation r = reservationWith(ReservationStatus.PENDING_CONFIRMATION,
                Participant.owner(OWNER_ID, ReservationChannel.WEB),
                Participant.registered(JOINER_ID, 2, ReservationChannel.WEB));
        when(reservationCommandPort.findByIdForUpdate(RES_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.unirse(RES_ID, JOINER_ID))
                .isInstanceOf(ParticipacionDuplicadaException.class);
        verify(participantCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("reserva completa → 422 PARTICIPANTS_LIMIT_EXCEEDED")
    void should_reject_when_full() {
        configWithMax(2);
        Reservation r = reservationWith(ReservationStatus.CONFIRMED,
                Participant.owner(OWNER_ID, ReservationChannel.WEB),
                Participant.registered(3L, 2, ReservationChannel.WEB));
        when(reservationCommandPort.findByIdForUpdate(RES_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.unirse(RES_ID, JOINER_ID))
                .isInstanceOf(InvalidReservaStateException.class)
                .extracting(e -> ((InvalidReservaStateException) e).getCode())
                .isEqualTo("PARTICIPANTS_LIMIT_EXCEEDED");
        verify(participantCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("unión exitosa → añade participante en el siguiente slot, invalida caché, devuelve datos")
    void should_join_successfully_and_assign_next_slot() {
        configWithMax(4);
        Reservation r = reservationWith(ReservationStatus.PENDING_CONFIRMATION,
                Participant.owner(OWNER_ID, ReservationChannel.WEB));
        when(reservationCommandPort.findByIdForUpdate(RES_ID)).thenReturn(Optional.of(r));
        when(participantCommandPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User joiner = new User("jane", "hash", "Jane", "Smith", "jane@example.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        when(userRepositoryPort.findById(JOINER_ID)).thenReturn(Optional.of(joiner));
        when(paymentCommandPort.findByReservationId(RES_ID))
                .thenReturn(Optional.of(Payment.pendingFor(RES_ID, new BigDecimal("15.00"))));

        UnirseResponse response = service.unirse(RES_ID, JOINER_ID);

        assertThat(response.reservaId()).isEqualTo(RES_ID.toString());
        assertThat(response.userId()).isEqualTo(JOINER_ID);
        assertThat(response.nombre()).isEqualTo("Jane Smith");
        assertThat(response.statusPago()).isEqualTo("PENDING");

        // The joined participant is inserted at the next slot as a non-owner.
        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantCommandPort).save(captor.capture());
        Participant added = captor.getValue();
        assertThat(added.getUserId()).isEqualTo(JOINER_ID);
        assertThat(added.getSlotPosition()).isEqualTo(2);
        assertThat(added.isOwner()).isFalse();
        // Both sides of the association are kept in sync so the FK is set on INSERT.
        assertThat(r.getParticipants()).hasSize(2);
        verify(cacheInvalidator).invalidate(r.getReservationDate());
    }
}
