package com.padelpro.reservas.application.service;

import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for {@link ReservaMapper} (change backend-branch-coverage, D1/D2):
 * the PII-minimization toggle (RN-RGPD-03), participant slot ordering, and the null-guards on the
 * reservation/payment fields. Same package so the package-private mapper is reachable.
 */
@DisplayName("Unit — ReservaMapper")
class ReservaMapperTest {

    private static final UUID RES = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private static Reservation fullReservation() {
        Participant owner = Participant.owner(1L, ReservationChannel.WEB);           // slot 1
        Participant guest = Participant.external("Invitada", "+34600111222", 2, ReservationChannel.WEB);
        return Reservation.builder()
                .id(RES).ownerId(1L)
                .reservationDate(LocalDate.of(2099, 6, 1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB)
                // deliberately out of order so the slot_position sort is exercised
                .participants(new java.util.ArrayList<>(List.of(guest, owner)))
                .build();
    }

    private static Payment fullPayment() {
        Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
        setPaymentId(p, UUID.randomUUID());  // assign an id (no cross-package onCreate access)
        p.markInProgress("ORDER1", "url");   // sets method + status
        return p;
    }

    private static void setPaymentId(Payment p, UUID id) {
        try {
            var f = Payment.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("owner/ADMIN view (includePii=true) exposes phone + notes and full payment")
    void with_pii_and_payment() {
        Reservation r = fullReservation();

        ReservaResponse response = ReservaMapper.toResponse(r, fullPayment(), true);

        assertThat(response.id()).isEqualTo(RES.toString());
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.channel()).isEqualTo("WEB");
        assertThat(response.notes()).isEqualTo(r.getNotes()); // owner view keeps notes (null here)
        assertThat(response.priceTotal()).isEqualByComparingTo("15.00");
        // participants sorted by slot; the external phone is visible to the owner
        assertThat(response.participants()).hasSize(2);
        assertThat(response.participants().get(0).slotPosition()).isEqualTo(1);
        assertThat(response.participants().get(1).externalPhone()).isEqualTo("+34600111222");
        assertThat(response.pago().method()).isEqualTo("REDSYS");
        assertThat(response.pago().status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("co-participant view (includePii=false, no payment) nulls phone/notes and pago/price")
    void without_pii_no_payment() {
        Reservation r = Reservation.builder()
                .id(RES).ownerId(1L)
                .reservationDate(LocalDate.of(2099, 6, 1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60).status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB)
                .participants(new java.util.ArrayList<>(List.of(
                        Participant.external("Invitada", "+34600111222", 2, ReservationChannel.WEB))))
                .build();

        ReservaResponse response = ReservaMapper.toResponse(r, null, false);

        assertThat(response.notes()).isNull();
        assertThat(response.participants().get(0).externalPhone()).isNull();
        assertThat(response.priceTotal()).isNull();
        assertThat(response.pago()).isNull();
    }

    @Test
    @DisplayName("transient reservation + payment with null id/method/status → null-guard branches")
    void null_guard_branches() {
        // Reservation with null id/status/channel (builder leaves them unset).
        Reservation r = Reservation.builder()
                .ownerId(1L)
                .reservationDate(LocalDate.of(2099, 6, 1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .durationMinutes(60)
                .participants(new java.util.ArrayList<>())
                .build();
        Payment p = Payment.pendingFor(RES, new BigDecimal("15.00"));
        p.setStatus(null); // null id (no onCreate), null method, null status

        ReservaResponse response = ReservaMapper.toResponse(r, p, true);

        assertThat(response.id()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.channel()).isNull();
        assertThat(response.priceTotal()).isEqualByComparingTo("15.00");
        assertThat(response.pago().id()).isNull();
        assertThat(response.pago().method()).isNull();
        assertThat(response.pago().status()).isNull();
    }
}
