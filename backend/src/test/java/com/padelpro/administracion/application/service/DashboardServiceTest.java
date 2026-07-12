package com.padelpro.administracion.application.service;

import com.padelpro.administracion.application.dto.IngresosResponse;
import com.padelpro.administracion.application.dto.OcupacionResponse;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.domain.model.PaymentMethod;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService} — admin dashboard aggregation (capability
 * administracion-club, RN-ADM-02/03/04, RN-RGPD-04). Pure logic with the repositories and the
 * system-config port mocked; no database involved. Schedule defaults: 08:00–23:00, 60-min slots ⇒
 * 15 available slots per day.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService — agregación del dashboard admin")
class DashboardServiceTest {

    private static final LocalDate DIA = LocalDate.of(2025, 5, 1);
    private static final LocalDate FIN_MES = LocalDate.of(2025, 5, 31);

    @Mock
    private PaymentJpaRepository paymentRepository;

    @Mock
    private ReservationJpaRepository reservationRepository;

    @Mock
    private SystemConfigRepositoryPort systemConfigRepositoryPort;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(paymentRepository, reservationRepository, systemConfigRepositoryPort);
    }

    private void configPresent() {
        SystemConfig config = SystemConfig.builder()
                .id(1L)
                .clubName("Club")
                .pistaState(PistaState.ACTIVA)
                .paymentGateway(PaymentGateway.CASH)
                .maxParticipantsPerPista(4)
                .pricePerHour(new BigDecimal("20.00"))
                .cancellationDeadlineHours(24)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.of(config));
    }

    private void configAbsent() {
        when(systemConfigRepositoryPort.findById(1L)).thenReturn(Optional.empty());
    }

    // -----------------------------------------------------------------
    // Ocupación (RN-ADM-02)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("ocupacion: con reservas calcula % sobre slots disponibles del día")
    void ocupacion_conReservas_calculaPorcentaje() {
        configPresent();
        // 3 reservations of 60 min in a single day => 3 reserved slots of 15 available => 20.0%
        when(reservationRepository.sumActiveDurationMinutesInRange(DIA, DIA, ReservationStatus.CANCELLED)).thenReturn(180L);

        OcupacionResponse resp = service.ocupacion(DIA, DIA);

        assertThat(resp.fechaInicio()).isEqualTo(DIA);
        assertThat(resp.fechaFin()).isEqualTo(DIA);
        assertThat(resp.slotsDisponibles()).isEqualTo(15);
        assertThat(resp.slotsReservados()).isEqualTo(3);
        assertThat(resp.ocupacionPct()).isEqualByComparingTo(new BigDecimal("20.0"));
    }

    @Test
    @DisplayName("ocupacion: sin reservas devuelve 0% con slots disponibles > 0")
    void ocupacion_sinReservas_devuelveCero() {
        configPresent();
        when(reservationRepository.sumActiveDurationMinutesInRange(DIA, DIA, ReservationStatus.CANCELLED)).thenReturn(0L);

        OcupacionResponse resp = service.ocupacion(DIA, DIA);

        assertThat(resp.slotsReservados()).isZero();
        assertThat(resp.slotsDisponibles()).isEqualTo(15);
        assertThat(resp.ocupacionPct()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("ocupacion: sin horario configurado ⇒ slotsDisponibles=0 y 0% (sin división por cero)")
    void ocupacion_sinConfig_slotsCeroYSinDivisionPorCero() {
        configAbsent();
        // Reservations exist, but without a configured schedule the denominator is 0.
        when(reservationRepository.sumActiveDurationMinutesInRange(DIA, DIA, ReservationStatus.CANCELLED)).thenReturn(120L);

        OcupacionResponse resp = service.ocupacion(DIA, DIA);

        assertThat(resp.slotsDisponibles()).isZero();
        assertThat(resp.ocupacionPct()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("ocupacion: rango invertido (fin < inicio) lanza ValidationException")
    void ocupacion_rangoInvertido_lanzaValidacion() {
        assertThatThrownBy(() -> service.ocupacion(FIN_MES, DIA))
                .isInstanceOf(ValidationException.class);
    }

    // -----------------------------------------------------------------
    // Ingresos (RN-ADM-03)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("ingresos: total con desglose por método (REDSYS + CASH)")
    void ingresos_conDesglose() {
        when(paymentRepository.sumPaidAmountGroupedByMethod(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{PaymentMethod.REDSYS, new BigDecimal("45.00")},
                new Object[]{PaymentMethod.CASH, new BigDecimal("30.00")}
        ));

        IngresosResponse resp = service.ingresos(DIA, FIN_MES);

        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(resp.porMetodo()).containsKeys("REDSYS", "CASH");
        assertThat(resp.porMetodo().get("REDSYS")).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(resp.porMetodo().get("CASH")).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("ingresos: rango sin pagos ⇒ total 0.00 y ambos métodos 0.00 (HTTP 200)")
    void ingresos_sinPagos_devuelveCeros() {
        when(paymentRepository.sumPaidAmountGroupedByMethod(any(), any(), any())).thenReturn(List.of());

        IngresosResponse resp = service.ingresos(DIA, FIN_MES);

        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(resp.porMetodo().get("REDSYS")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.porMetodo().get("CASH")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------
    // Export CSV (RN-ADM-04, RN-RGPD-04)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("exportarCsv: cabecera correcta, una fila por día y valores agregados con punto decimal")
    void exportarCsv_contenidoCorrecto() {
        configPresent();
        LocalDate fin = LocalDate.of(2025, 5, 2); // 2-day range
        // Day 1: 2 reservations totalling 120 min => 2 slots of 15 => 13.3%
        when(reservationRepository.findActiveUsageRowsInRange(DIA, fin, ReservationStatus.CANCELLED))
                .thenReturn(List.<Object[]>of(
                new Object[]{DIA, 60},
                new Object[]{DIA, 60}
        ));
        // Day 1: one REDSYS payment of 45.00
        OffsetDateTime paidDay1 = DIA.atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        when(paymentRepository.findPaidRowsInRange(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{paidDay1, new BigDecimal("45.00"), PaymentMethod.REDSYS}
        ));

        String csv = service.exportarCsv(DIA, fin);
        String[] lines = csv.split("\r\n");

        assertThat(lines[0]).isEqualTo("fecha,reservas,ocupacion_pct,ingresos_total,ingresos_redsys,ingresos_cash");
        assertThat(lines).hasSize(3); // header + 2 days
        assertThat(lines[1]).isEqualTo("2025-05-01,2,13.3,45.00,45.00,0.00");
        assertThat(lines[2]).isEqualTo("2025-05-02,0,0.0,0.00,0.00,0.00");
    }

    @Test
    @DisplayName("exportarCsv: no expone datos personales (RN-RGPD-04)")
    void exportarCsv_sinDatosPersonales() {
        configPresent();
        OffsetDateTime paid = DIA.atTime(9, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        when(reservationRepository.findActiveUsageRowsInRange(DIA, DIA, ReservationStatus.CANCELLED)).thenReturn(List.<Object[]>of(
                new Object[]{DIA, 90}
        ));
        when(paymentRepository.findPaidRowsInRange(any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{paid, new BigDecimal("22.50"), PaymentMethod.CASH}
        ));

        String csv = service.exportarCsv(DIA, DIA);

        // Only aggregate columns — never an email, and no free-text owner/participant identity.
        assertThat(csv).doesNotContain("@");
        assertThat(csv.toLowerCase()).doesNotContain("email");
        assertThat(csv.toLowerCase()).doesNotContain("nombre");
        assertThat(csv.toLowerCase()).doesNotContain("owner");
        assertThat(csv).contains("2025-05-01,1,");
        assertThat(csv).contains(",0.00,22.50"); // ingresos_redsys 0.00, ingresos_cash 22.50
    }

    @Test
    @DisplayName("exportarCsv: sin horario configurado ⇒ ocupacion_pct 0.0 por día")
    void exportarCsv_sinConfig_ocupacionCero() {
        configAbsent();
        when(reservationRepository.findActiveUsageRowsInRange(DIA, DIA, ReservationStatus.CANCELLED)).thenReturn(List.<Object[]>of(
                new Object[]{DIA, 120}
        ));
        lenient().when(paymentRepository.findPaidRowsInRange(any(), any(), any())).thenReturn(List.of());

        String csv = service.exportarCsv(DIA, DIA);
        String[] lines = csv.split("\r\n");

        assertThat(lines[1]).isEqualTo("2025-05-01,1,0.0,0.00,0.00,0.00");
    }
}
