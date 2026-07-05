package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.PartidaAbiertaResponse;
import com.padelpro.reservas.domain.model.Participant;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PartidasQueryService} — open-matches listing (capability partidas, D1).
 * Ports/repositories mocked; no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PartidasQueryService — listado de partidas abiertas")
class PartidasQueryServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2025, 8, 1);

    @Mock private ReservationJpaRepository reservationRepository;
    @Mock private PaymentJpaRepository paymentRepository;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private SystemConfigRepositoryPort systemConfigRepositoryPort;

    private PartidasQueryService service;

    @BeforeEach
    void setUp() {
        service = new PartidasQueryService(reservationRepository, paymentRepository,
                userRepositoryPort, systemConfigRepositoryPort);
        SystemConfig config = SystemConfig.builder()
                .id(1L).clubName("Club").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH).maxParticipantsPerPista(4)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        lenient().when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.of(config));
    }

    private Reservation reservation(UUID id, LocalTime start, List<Participant> participants) {
        return Reservation.builder()
                .id(id).ownerId(1L).reservationDate(FECHA)
                .startTime(start).endTime(start.plusMinutes(60))
                .durationMinutes(60).status(ReservationStatus.PENDING_CONFIRMATION)
                .channel(ReservationChannel.WEB).participants(participants).build();
    }

    private User user(long id, String first, String last) {
        User u = new User("u" + id, "hash", first, last, "u" + id + "@example.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        setId(u, id);
        return u;
    }

    private void setId(User u, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("sin reservas activas → lista vacía")
    void should_return_empty_when_no_reservations() {
        when(reservationRepository.findActiveByDateWithParticipants(any(), anyList()))
                .thenReturn(List.of());

        assertThat(service.listOpenByDate(FECHA)).isEmpty();
    }

    @Test
    @DisplayName("reserva completa (4/4) no aparece; incompleta sí, con plazasLibres y participantes")
    void should_exclude_full_and_project_open_matches() {
        UUID openId = UUID.randomUUID();
        UUID fullId = UUID.randomUUID();

        Reservation open = reservation(openId, LocalTime.of(18, 0), List.of(
                Participant.owner(1L, ReservationChannel.WEB)));
        Reservation full = reservation(fullId, LocalTime.of(19, 0), List.of(
                Participant.owner(2L, ReservationChannel.WEB),
                Participant.registered(3L, 2, ReservationChannel.WEB),
                Participant.registered(4L, 3, ReservationChannel.WEB),
                Participant.registered(5L, 4, ReservationChannel.WEB)));

        when(reservationRepository.findActiveByDateWithParticipants(any(), anyList()))
                .thenReturn(List.of(open, full));
        when(paymentRepository.findByReservationIdIn(anyList()))
                .thenReturn(List.of(Payment.pendingFor(openId, new BigDecimal("15.00"))));
        when(userRepositoryPort.findAllById(anyList()))
                .thenReturn(List.of(user(1L, "Ana", "García")));

        List<PartidaAbiertaResponse> result = service.listOpenByDate(FECHA);

        assertThat(result).hasSize(1);
        PartidaAbiertaResponse p = result.get(0);
        assertThat(p.reservaId()).isEqualTo(openId.toString());
        assertThat(p.plazasLibres()).isEqualTo(3);
        assertThat(p.priceTotal()).isEqualByComparingTo("15.00");
        assertThat(p.participantes()).hasSize(1);
        assertThat(p.participantes().get(0).nombre()).isEqualTo("Ana García");
        assertThat(p.participantes().get(0).owner()).isTrue();
    }

    @Test
    @DisplayName("varias partidas abiertas → ordenadas por hora de inicio")
    void should_sort_open_matches_by_start_time() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Reservation later = reservation(a, LocalTime.of(20, 0), List.of(
                Participant.owner(1L, ReservationChannel.WEB)));
        Reservation earlier = reservation(b, LocalTime.of(9, 0), List.of(
                Participant.owner(2L, ReservationChannel.WEB)));

        when(reservationRepository.findActiveByDateWithParticipants(any(), anyList()))
                .thenReturn(List.of(later, earlier));
        when(paymentRepository.findByReservationIdIn(anyList())).thenReturn(List.of());
        when(userRepositoryPort.findAllById(anyList()))
                .thenReturn(List.of(user(1L, "A", "A"), user(2L, "B", "B")));

        List<PartidaAbiertaResponse> result = service.listOpenByDate(FECHA);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.of(20, 0));
    }
}
