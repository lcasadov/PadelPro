package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.application.dto.CrearReservaRequest.ParticipanteAdicional;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.model.IdempotencyKey;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage unit tests for {@link CrearReservaService} (change backend-branch-coverage, D1/D2):
 * input validation (400), idempotency short-circuit (D2), participant XOR + membership guards, and
 * the atomic create + cache invalidation happy path (US-007, RN-RES-01/03/05).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit — CrearReservaService")
class CrearReservaServiceTest {

    private static final long OWNER = 7L;
    private static final String FUTURE_DATE = "2099-06-01";

    @Mock private ReservationCommandPort reservationCommandPort;
    @Mock private PaymentCommandPort paymentCommandPort;
    @Mock private SystemConfigRepositoryPort systemConfigRepositoryPort;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private DisponibilidadCacheInvalidator cacheInvalidator;

    private CrearReservaService service;

    @BeforeEach
    void setUp() {
        service = new CrearReservaService(reservationCommandPort, paymentCommandPort,
                systemConfigRepositoryPort, userRepositoryPort, cacheInvalidator);
    }

    private SystemConfig config(int maxParticipants) {
        return SystemConfig.builder()
                .id(1L).clubName("Club").pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(maxParticipants)
                .pricePerHour(new BigDecimal("10.00"))
                .cancellationDeadlineHours(24)
                .build();
    }

    private CrearReservaRequest request(String date, String time, Integer duration,
                                        List<ParticipanteAdicional> extras) {
        return new CrearReservaRequest(date, time, duration, "notes", extras);
    }

    private void stubConfig(int max) {
        when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.of(config(max)));
    }

    private void stubSaveHappyPath() {
        when(reservationCommandPort.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentCommandPort.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Happy paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("owner-only reservation is created, priced and the cache invalidated (no idem key)")
    void creates_owner_only() {
        stubConfig(4);
        stubSaveHappyPath();

        ReservaResponse response = service.crear(OWNER, request(FUTURE_DATE, "18:00", 90, null), null);

        assertThat(response.ownerId()).isEqualTo(OWNER);
        // price = 10.00 × 90 / 60 = 15.00
        assertThat(response.priceTotal()).isEqualByComparingTo("15.00");
        assertThat(response.participants()).hasSize(1);
        verify(cacheInvalidator).invalidate(any());
        // no idempotency key was supplied → none recorded
        verify(reservationCommandPort, never()).saveIdempotencyKey(any());
    }

    @Test
    @DisplayName("the three-argument overload defaults to the WEB channel")
    void defaults_to_web_channel() {
        stubConfig(4);
        stubSaveHappyPath();

        ReservaResponse response = service.crear(OWNER, request(FUTURE_DATE, "18:00", 90, null), null);

        assertThat(response.channel()).isEqualTo("WEB");
    }

    @Test
    @DisplayName("an explicit TELEGRAM channel is applied to the reservation and its participants")
    void telegram_channel_applied() {
        stubConfig(4);
        stubSaveHappyPath();
        org.mockito.ArgumentCaptor<Reservation> saved =
                org.mockito.ArgumentCaptor.forClass(Reservation.class);

        ReservaResponse response = service.crear(OWNER, request(FUTURE_DATE, "18:00", 90,
                        List.of(new ParticipanteAdicional(null, "Invitado", null))),
                null, com.padelpro.reservas.domain.model.ReservationChannel.TELEGRAM);

        assertThat(response.channel()).isEqualTo("TELEGRAM");
        verify(reservationCommandPort).save(saved.capture());
        assertThat(saved.getValue().getChannel())
                .isEqualTo(com.padelpro.reservas.domain.model.ReservationChannel.TELEGRAM);
        assertThat(saved.getValue().getParticipants())
                .allSatisfy(p -> assertThat(p.getJoinedVia())
                        .isEqualTo(com.padelpro.reservas.domain.model.ReservationChannel.TELEGRAM));
    }

    @Test
    @DisplayName("blank idempotency key is treated as absent (short-circuit skipped, key not recorded)")
    void blank_idem_key_is_absent() {
        stubConfig(4);
        stubSaveHappyPath();

        service.crear(OWNER, request(FUTURE_DATE, "18:30", 60, null), "   ");

        verify(reservationCommandPort, never()).findIdempotencyKey(any(), any());
        verify(reservationCommandPort, never()).saveIdempotencyKey(any());
    }

    @Test
    @DisplayName("new idempotency key is recorded after a successful create")
    void records_idem_key_after_create() {
        when(reservationCommandPort.findIdempotencyKey(OWNER, "idem-1")).thenReturn(Optional.empty());
        stubConfig(4);
        stubSaveHappyPath();

        service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, null), " idem-1 ");

        verify(reservationCommandPort).saveIdempotencyKey(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("idempotency replay returns the existing reservation without re-creating")
    void idempotency_replay() {
        UUID existingId = UUID.randomUUID();
        when(reservationCommandPort.findIdempotencyKey(OWNER, "idem-1"))
                .thenReturn(Optional.of(new IdempotencyKey("idem-1", OWNER, existingId)));
        Reservation existing = Reservation.builder()
                .id(existingId).ownerId(OWNER)
                .reservationDate(java.time.LocalDate.of(2099, 6, 1))
                .startTime(java.time.LocalTime.of(18, 0)).endTime(java.time.LocalTime.of(19, 0))
                .durationMinutes(60).status(com.padelpro.reservas.domain.model.ReservationStatus.PENDING_CONFIRMATION)
                .channel(com.padelpro.reservas.domain.model.ReservationChannel.WEB).build();
        when(reservationCommandPort.findById(existingId)).thenReturn(Optional.of(existing));
        when(paymentCommandPort.findByReservationId(existingId))
                .thenReturn(Optional.of(Payment.pendingFor(existingId, new BigDecimal("15.00"))));

        ReservaResponse response = service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, null), "idem-1");

        assertThat(response.id()).isEqualTo(existingId.toString());
        // no new reservation persisted on replay
        verify(reservationCommandPort, never()).save(any());
        verify(cacheInvalidator, never()).invalidate(any());
    }

    @Test
    @DisplayName("external + registered additional participants are attached (within the limit)")
    void additional_participants_attached() {
        stubConfig(4);
        stubSaveHappyPath();
        when(userRepositoryPort.existsByIdAndStatus(99L, UserStatus.ACTIVE)).thenReturn(true);

        List<ParticipanteAdicional> extras = List.of(
                new ParticipanteAdicional(99L, null, null),           // registered
                new ParticipanteAdicional(null, "Invitada", "+34600"));// external
        ReservaResponse response = service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, extras), null);

        assertThat(response.participants()).hasSize(3); // owner + 2
    }

    // -------------------------------------------------------------------------
    // Input validation (400)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("blank date → ValidationException before any config load")
    void blank_date_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request("  ", "18:00", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reservationDate");
        verify(systemConfigRepositoryPort, never()).findById(any());
    }

    @Test
    @DisplayName("malformed date → ValidationException")
    void malformed_date_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request("2099-13-40", "18:00", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    @DisplayName("blank time → ValidationException")
    void blank_time_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, " ", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("malformed time → ValidationException")
    void malformed_time_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "25:99", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    @DisplayName("time not on :00/:30 → ValidationException")
    void time_off_grid_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:15", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("en punto o en media");
    }

    @Test
    @DisplayName("null duration → ValidationException")
    void null_duration_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", null, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("durationMinutes");
    }

    @Test
    @DisplayName("non-allowed duration → ValidationException")
    void bad_duration_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", 45, null), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("past date/time → ValidationException")
    void past_datetime_rejected() {
        assertThatThrownBy(() -> service.crear(OWNER, request("2000-01-01", "18:00", 60, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("futuro");
    }

    // -------------------------------------------------------------------------
    // Participant + limit guards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("participants over the pista limit → InvalidReservaStateException")
    void participants_limit_exceeded() {
        stubConfig(2); // owner + max 1 extra
        List<ParticipanteAdicional> extras = List.of(
                new ParticipanteAdicional(null, "A", null),
                new ParticipanteAdicional(null, "B", null));

        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, extras), null))
                .isInstanceOf(InvalidReservaStateException.class)
                .satisfies(ex -> assertThat(((InvalidReservaStateException) ex).getCode())
                        .isEqualTo("PARTICIPANTS_LIMIT_EXCEEDED"));
        verify(reservationCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("additional participant that is both a user AND an external guest → ValidationException")
    void participant_both_user_and_external() {
        stubConfig(4);
        List<ParticipanteAdicional> extras = List.of(new ParticipanteAdicional(99L, "Guest", null));

        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, extras), null))
                .isInstanceOf(ValidationException.class);
        verify(reservationCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("additional participant that is neither user nor external → ValidationException")
    void participant_neither_user_nor_external() {
        stubConfig(4);
        List<ParticipanteAdicional> extras = List.of(new ParticipanteAdicional(null, "  ", null));

        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, extras), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("registered participant that is not an ACTIVE member → InvalidReservaStateException")
    void registered_participant_inactive() {
        stubConfig(4);
        when(userRepositoryPort.existsByIdAndStatus(99L, UserStatus.ACTIVE)).thenReturn(false);
        List<ParticipanteAdicional> extras = List.of(new ParticipanteAdicional(99L, null, null));

        assertThatThrownBy(() -> service.crear(OWNER, request(FUTURE_DATE, "18:00", 60, extras), null))
                .isInstanceOf(InvalidReservaStateException.class)
                .satisfies(ex -> assertThat(((InvalidReservaStateException) ex).getCode())
                        .isEqualTo("PARTICIPANT_NOT_FOUND"));
        verify(reservationCommandPort, never()).save(any());
    }
}
