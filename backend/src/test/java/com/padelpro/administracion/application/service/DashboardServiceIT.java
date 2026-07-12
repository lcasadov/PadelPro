package com.padelpro.administracion.application.service;

import com.padelpro.administracion.application.dto.IngresosResponse;
import com.padelpro.administracion.application.dto.OcupacionResponse;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression integration test for {@link DashboardService} against a real PostgreSQL 15 (PR #217
 * bug). The three dashboard endpoints returned 500 in production because the dashboard JPQL used enum
 * literals (e.g. {@code r.status <> ...ReservationStatus.CANCELLED}), which Hibernate 6 translated to
 * a cast to a non-existent Postgres enum type ({@code 'CANCELLED'::reservationstatus}). The
 * {@code status} columns are VARCHAR with {@code @Enumerated(STRING)}, so the cast blew up with
 * {@code type "reservationstatus" does not exist}.
 *
 * <p>The pre-existing unit tests mocked the repositories, so they never exercised the real SQL and
 * could not catch this. This test seeds real rows and drives {@code ocupacion}, {@code ingresos} and
 * {@code exportarCsv} end to end, asserting they execute the generated SQL and return real data with
 * no {@code PSQLException}. It runs on CI Linux with a real Docker/Postgres; locally it may fail to
 * start the Testcontainers container (Docker 29 quirk), which is expected and unrelated to the fix.
 */
@DisplayName("IT — DashboardService sobre Postgres real (regresión enum-literal, PR #217)")
class DashboardServiceIT extends PostgresIntegrationTest {

    private static final LocalDate FECHA_INICIO = LocalDate.of(2026, 3, 1);
    private static final LocalDate FECHA_FIN = LocalDate.of(2026, 3, 31);
    private static final LocalDate DIA_RESERVA = LocalDate.of(2026, 3, 15);
    private static final BigDecimal IMPORTE = new BigDecimal("20.00");

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationJpaRepository reservationRepository;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Seeds one CONFIRMED (active) reservation, one CANCELLED reservation (which must NOT count), and
     * one PAID/CASH payment for the active reservation, then persists them via the real repositories.
     *
     * @return the id of the seeded owner user
     */
    private Long seedActiveReservationAndPaidPayment() {
        OffsetDateTime now = OffsetDateTime.now();
        User owner = userRepository.saveAndFlush(new User(
                "dash.owner", passwordEncoder.encode("Password1"), "Dash", "Owner",
                "dash.owner@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));

        Reservation active = reservationRepository.saveAndFlush(Reservation.builder()
                .id(UUID.randomUUID())
                .ownerId(owner.getId())
                .reservationDate(DIA_RESERVA)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .durationMinutes(60)
                .status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB)
                .build());

        // A cancelled reservation on the same day at a different slot: it must be excluded by the
        // `status <> :cancelled` filter, i.e. it must not add to occupancy.
        reservationRepository.saveAndFlush(Reservation.builder()
                .id(UUID.randomUUID())
                .ownerId(owner.getId())
                .reservationDate(DIA_RESERVA)
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(12, 0))
                .durationMinutes(60)
                .status(ReservationStatus.CANCELLED)
                .channel(ReservationChannel.WEB)
                .build());

        OffsetDateTime paidAt = DIA_RESERVA.atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        Payment payment = Payment.pendingFor(active.getId(), IMPORTE);
        payment.markCashPaid(owner.getId(), paidAt);
        paymentRepository.saveAndFlush(payment);

        return owner.getId();
    }

    @Test
    @DisplayName("ocupacion ejecuta el SQL real (sum de duración, excluye CANCELLED) sin PSQLException")
    void ocupacion_ejecutaSqlRealSinExcepcion() {
        seedActiveReservationAndPaidPayment();

        OcupacionResponse resp = dashboardService.ocupacion(FECHA_INICIO, FECHA_FIN);

        // Only the CONFIRMED 60-min reservation counts (1 slot); the CANCELLED one is filtered out.
        assertThat(resp.slotsReservados()).isEqualTo(1);
        assertThat(resp.slotsDisponibles()).isPositive();
        assertThat(resp.ocupacionPct()).isNotNull();
    }

    @Test
    @DisplayName("ingresos ejecuta el SQL real (sum GROUP BY method, filtra PAID) sin PSQLException")
    void ingresos_ejecutaSqlRealSinExcepcion() {
        seedActiveReservationAndPaidPayment();

        IngresosResponse resp = dashboardService.ingresos(FECHA_INICIO, FECHA_FIN);

        assertThat(resp.total()).isEqualByComparingTo(IMPORTE);
        assertThat(resp.porMetodo().get("CASH")).isEqualByComparingTo(IMPORTE);
        assertThat(resp.porMetodo().get("REDSYS")).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("exportarCsv ejecuta ambos SQL reales y emite datos agregados sin PSQLException")
    void exportarCsv_ejecutaSqlRealSinExcepcion() {
        seedActiveReservationAndPaidPayment();

        String csv = dashboardService.exportarCsv(FECHA_INICIO, FECHA_FIN);

        assertThat(csv).startsWith(
                "fecha,reservas,ocupacion_pct,ingresos_total,ingresos_redsys,ingresos_cash");
        // The seeded day: 1 reservation and 20.00 total income all in the CASH column.
        assertThat(csv).contains("2026-03-15,1,");
        assertThat(csv).contains(",20.00,0.00,20.00");
    }

    @Test
    @DisplayName("los tres endpoints no lanzan excepción con la BD sembrada (regresión #217)")
    void tresEndpoints_noLanzanExcepcion() {
        seedActiveReservationAndPaidPayment();

        assertThatCode(() -> {
            dashboardService.ocupacion(FECHA_INICIO, FECHA_FIN);
            dashboardService.ingresos(FECHA_INICIO, FECHA_FIN);
            dashboardService.exportarCsv(FECHA_INICIO, FECHA_FIN);
        }).doesNotThrowAnyException();
    }
}
