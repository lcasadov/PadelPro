package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.application.dto.DisponibilidadResponse;
import com.padelpro.reservas.application.dto.TramoDisponible;
import com.padelpro.reservas.domain.model.ReservationOccupancy;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisponibilidadService} — slot calculation (capability disponibilidad-pistas).
 *
 * <p>Covers tasks 3.1–3.6 and the spec scenarios (RN-RES-01, RN-RES-02, RN-RES-04). Pure logic with
 * both outbound ports mocked; no database involved. Schedule defaults: 08:00–23:00, 60-min slots
 * ⇒ 15 slots per day when the court is fully free.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisponibilidadService — cálculo de tramos libres")
class DisponibilidadServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2025, 8, 1);
    private static final int TOTAL_SLOTS = 23 - 8; // 08..22 inclusive => 15 slots

    @Mock
    private ReservationQueryPort reservationQueryPort;

    @Mock
    private SystemConfigRepositoryPort systemConfigRepositoryPort;

    private DisponibilidadService service;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadService(reservationQueryPort, systemConfigRepositoryPort);
    }

    private void configWith(PistaState state, int maxParticipants) {
        SystemConfig config = SystemConfig.builder()
                .id(1L)
                .clubName("Club")
                .pistaState(state)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(maxParticipants)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.of(config));
    }

    private TramoDisponible slotAt(DisponibilidadResponse resp, String horaInicio) {
        return resp.tramosDisponibles().stream()
                .filter(t -> t.horaInicio().equals(horaInicio))
                .findFirst()
                .orElse(null);
    }

    // 3.1 — Día sin ninguna reserva activa → todos los tramos con plazasLibres = max_participants
    @Test
    @DisplayName("3.1 should_return_all_slots_free_when_no_active_reservations")
    void should_return_all_slots_free_when_no_active_reservations() {
        configWith(PistaState.ACTIVA, 4);
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of());

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        assertThat(resp.fecha()).isEqualTo("2025-08-01");
        assertThat(resp.tramosDisponibles()).hasSize(TOTAL_SLOTS);
        assertThat(resp.tramosDisponibles())
                .allSatisfy(t -> {
                    assertThat(t.plazasLibres()).isEqualTo(4);
                    assertThat(t.duracionMinutos()).isEqualTo(60);
                    // creable == true when the slot is fully empty (plazasLibres == max_participants)
                    assertThat(t.creable()).isTrue();
                });
        assertThat(slotAt(resp, "08:00")).isNotNull();
        assertThat(slotAt(resp, "22:00")).isNotNull();
        assertThat(slotAt(resp, "23:00")).isNull(); // closing hour is exclusive
    }

    // 3.2 — Reserva CONFIRMED 10:00-11:00 con 2 participantes, max 4 → tramo 10:00 con plazasLibres = 2
    @Test
    @DisplayName("3.2 should_return_partial_slots_when_reservation_incomplete")
    void should_return_partial_slots_when_reservation_incomplete() {
        configWith(PistaState.ACTIVA, 4);
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(LocalTime.of(10, 0), LocalTime.of(11, 0), 2)
        ));

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        TramoDisponible t10 = slotAt(resp, "10:00");
        assertThat(t10).isNotNull();
        assertThat(t10.plazasLibres()).isEqualTo(2);
        assertThat(t10.duracionMinutos()).isEqualTo(60);
        // Partial slot (plazasLibres < max_participants) is NOT creable, only joinable
        assertThat(t10.creable()).isFalse();
        // Neighbouring slots remain fully free (and therefore creable)
        assertThat(slotAt(resp, "09:00").plazasLibres()).isEqualTo(4);
        assertThat(slotAt(resp, "09:00").creable()).isTrue();
        assertThat(slotAt(resp, "11:00").plazasLibres()).isEqualTo(4);
        assertThat(slotAt(resp, "11:00").creable()).isTrue();
    }

    // creable — flag aditivo (D7): true sii el tramo está totalmente vacío; false si hay reserva incompleta
    @Test
    @DisplayName("creable: true en tramo vacío, false en tramo con reserva incompleta")
    void should_set_creable_flag_from_full_emptiness() {
        configWith(PistaState.ACTIVA, 4);
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(LocalTime.of(9, 0), LocalTime.of(10, 0), 1),  // 09:00 -> 3 libres, incompleto
                new ReservationOccupancy(LocalTime.of(15, 0), LocalTime.of(16, 0), 3)   // 15:00 -> 1 libre, incompleto
        ));

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        // Tramo vacío -> creable true
        assertThat(slotAt(resp, "08:00").creable()).isTrue();
        // Tramos con reserva incompleta (con plazas libres) -> creable false
        assertThat(slotAt(resp, "09:00").creable()).isFalse();
        assertThat(slotAt(resp, "15:00").creable()).isFalse();
    }

    // 3.3 — PENDING_CONFIRMATION ocupa igual que CONFIRMED
    @Test
    @DisplayName("3.3 should_count_pending_confirmation_as_occupying")
    void should_count_pending_confirmation_as_occupying() {
        configWith(PistaState.ACTIVA, 4);
        // The query port only returns active occupants (PENDING_CONFIRMATION + CONFIRMED);
        // a pending reservation of 1 participant at 18:00-19:00 reduces seats by 1.
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(LocalTime.of(18, 0), LocalTime.of(19, 0), 1)
        ));

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        TramoDisponible t18 = slotAt(resp, "18:00");
        assertThat(t18).isNotNull();
        assertThat(t18.plazasLibres()).isEqualTo(3);
    }

    // 3.4 — CANCELLED no ocupa. The query port excludes cancelled rows, so an empty result means free.
    @Test
    @DisplayName("3.4 should_ignore_cancelled_reservations")
    void should_ignore_cancelled_reservations() {
        configWith(PistaState.ACTIVA, 4);
        // Only a CANCELLED reservation existed at 12:00-13:00; the port returns no active occupants.
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of());

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        TramoDisponible t12 = slotAt(resp, "12:00");
        assertThat(t12).isNotNull();
        assertThat(t12.plazasLibres()).isEqualTo(4);
    }

    // 3.5 — Tramo lleno (0 plazas) se omite; vecinos con reservas parciales sí aparecen.
    @Test
    @DisplayName("3.5 should_hide_full_slots")
    void should_hide_full_slots() {
        configWith(PistaState.ACTIVA, 4);
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(LocalTime.of(9, 0), LocalTime.of(10, 0), 3),  // 09:00 -> 1 libre
                new ReservationOccupancy(LocalTime.of(11, 0), LocalTime.of(12, 0), 4)  // 11:00 -> 0 libres (omitido)
        ));

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        assertThat(slotAt(resp, "09:00").plazasLibres()).isEqualTo(1);
        assertThat(slotAt(resp, "10:00").plazasLibres()).isEqualTo(4);
        assertThat(slotAt(resp, "11:00")).isNull(); // full slot omitted
        // 14 visible slots (15 total minus the full one)
        assertThat(resp.tramosDisponibles()).hasSize(TOTAL_SLOTS - 1);
    }

    // 3.6 — MANTENIMIENTO → lista vacía sin revelar motivo (RN-RES-04)
    @Test
    @DisplayName("3.6 should_return_empty_list_when_pista_in_mantenimiento")
    void should_return_empty_list_when_pista_in_mantenimiento() {
        configWith(PistaState.MANTENIMIENTO, 4);
        // Query port must not even be consulted, but keep it lenient in case of refactor.
        lenient().when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of());

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        assertThat(resp.fecha()).isEqualTo("2025-08-01");
        assertThat(resp.tramosDisponibles()).isEmpty();
    }

    // Extra — reserva de 90 min solapa dos tramos consecutivos (tsrange '[)')
    @Test
    @DisplayName("extra: a 90-min reservation occupies both overlapped hourly slots")
    void should_occupy_both_slots_for_90_minute_reservation() {
        configWith(PistaState.ACTIVA, 4);
        when(reservationQueryPort.findActiveOccupanciesByDate(FECHA)).thenReturn(List.of(
                new ReservationOccupancy(LocalTime.of(10, 0), LocalTime.of(11, 30), 2)
        ));

        DisponibilidadResponse resp = service.getDisponibilidad(FECHA);

        assertThat(slotAt(resp, "10:00").plazasLibres()).isEqualTo(2);
        assertThat(slotAt(resp, "11:00").plazasLibres()).isEqualTo(2);
        assertThat(slotAt(resp, "12:00").plazasLibres()).isEqualTo(4); // 11:30 end is exclusive at 12:00
    }
}
