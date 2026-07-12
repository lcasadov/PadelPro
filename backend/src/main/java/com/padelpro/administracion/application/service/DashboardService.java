package com.padelpro.administracion.application.service;

import com.padelpro.administracion.application.dto.IngresosResponse;
import com.padelpro.administracion.application.dto.OcupacionResponse;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.domain.model.PaymentMethod;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates the admin dashboard metrics for the capability {@code administracion-club}: court
 * occupancy (RN-ADM-02), income by period with a per-method breakdown (RN-ADM-03) and a CSV usage
 * report (RN-ADM-04, RN-RGPD-04). Only ADMIN reaches these figures (RN-ADM-01), enforced by the
 * controller and the {@code /api/admin/**} security filter chain.
 *
 * <h2>Slot / opening-hours assumptions (RN-ADM-02, design D2)</h2>
 * {@code system_config} does not yet expose opening hours or slot granularity (data-model open
 * decision P4). Until it does, this service uses the same documented defaults as
 * {@code DisponibilidadService} — a single court open {@value #OPEN_HOUR}:00–{@value #CLOSE_HOUR}:00
 * with {@value #SLOT_MINUTES}-minute slots, i.e. {@value #SLOTS_PER_DAY} available slots per day.
 * Reserved slots are derived from each non-cancelled reservation's duration
 * ({@code duration_minutes / slot}); reservations are created slot-aligned so the aggregate
 * {@code SUM(duration)/slot} equals the sum of per-reservation slots. The presence of the
 * {@code system_config} row (id=1) gates availability: when it is absent the schedule is unknown, so
 * {@code slotsDisponibles = 0} and occupancy is {@code 0} (no division by zero).
 *
 * <h2>Time zone</h2>
 * {@code fechaInicio}/{@code fechaFin} are inclusive calendar dates. Income filters {@code paid_at}
 * (an {@code OffsetDateTime}) on the half-open instant range
 * {@code [fechaInicio 00:00, fechaFin+1 00:00)} using the JVM default zone, consistent with the rest
 * of the backend (which timestamps with {@code OffsetDateTime.now()}).
 *
 * <p>Wired explicitly as a bean (see {@code AdministracionConfig}) to keep the application layer free
 * of Spring stereotypes, mirroring {@code PagoQueryService}.
 */
public class DashboardService {

    /** Club opening hour (inclusive). Documented default until {@code system_config} exposes it. */
    static final int OPEN_HOUR = 8;
    /** Club closing hour (exclusive end of the last slot). Documented default. */
    static final int CLOSE_HOUR = 23;
    /** Slot granularity in minutes. Documented default. */
    static final int SLOT_MINUTES = 60;
    /** Available slots per day for a single court under the documented schedule (15). */
    static final int SLOTS_PER_DAY = (CLOSE_HOUR - OPEN_HOUR) * 60 / SLOT_MINUTES;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String CSV_HEADER =
            "fecha,reservas,ocupacion_pct,ingresos_total,ingresos_redsys,ingresos_cash";
    private static final String CRLF = "\r\n";

    private final PaymentJpaRepository paymentRepository;
    private final ReservationJpaRepository reservationRepository;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;

    public DashboardService(PaymentJpaRepository paymentRepository,
                            ReservationJpaRepository reservationRepository,
                            SystemConfigRepositoryPort systemConfigRepositoryPort) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
    }

    /**
     * Court occupancy for {@code [fechaInicio, fechaFin]} (RN-ADM-02). Reserved slots come from a DB
     * {@code SUM(duration_minutes)} of non-cancelled reservations; available slots from the club
     * schedule. Empty schedule ⇒ {@code slotsDisponibles = 0} and {@code ocupacionPct = 0}.
     *
     * @throws ValidationException when {@code fechaFin} precedes {@code fechaInicio}
     */
    @Transactional(readOnly = true)
    public OcupacionResponse ocupacion(LocalDate fechaInicio, LocalDate fechaFin) {
        validateRange(fechaInicio, fechaFin);
        int slotsDisponibles = slotsDisponibles(fechaInicio, fechaFin);
        long totalDurationMinutes =
                reservationRepository.sumActiveDurationMinutesInRange(fechaInicio, fechaFin);
        int slotsReservados = durationToSlots(totalDurationMinutes);
        BigDecimal ocupacionPct = occupancyPct(slotsReservados, slotsDisponibles);
        return new OcupacionResponse(fechaInicio, fechaFin, slotsReservados, slotsDisponibles, ocupacionPct);
    }

    /**
     * Income for {@code [fechaInicio, fechaFin]} with a per-method breakdown (RN-ADM-03). Aggregated
     * in the database via {@code SUM(amount) GROUP BY method} over PAID payments. {@code porMetodo}
     * always contains {@code REDSYS} and {@code CASH} (0.00 when absent).
     *
     * @throws ValidationException when {@code fechaFin} precedes {@code fechaInicio}
     */
    @Transactional(readOnly = true)
    public IngresosResponse ingresos(LocalDate fechaInicio, LocalDate fechaFin) {
        validateRange(fechaInicio, fechaFin);
        OffsetDateTime start = startOfDay(fechaInicio);
        OffsetDateTime end = startOfDay(fechaFin.plusDays(1));

        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        porMetodo.put(PaymentMethod.REDSYS.name(), zeroMoney());
        porMetodo.put(PaymentMethod.CASH.name(), zeroMoney());

        BigDecimal total = zeroMoney();
        for (Object[] row : paymentRepository.sumPaidAmountGroupedByMethod(start, end)) {
            PaymentMethod method = (PaymentMethod) row[0];
            BigDecimal amount = money(row[1]);
            total = total.add(amount);
            if (method != null) {
                porMetodo.merge(method.name(), amount, BigDecimal::add);
            }
        }
        return new IngresosResponse(fechaInicio, fechaFin, total, porMetodo);
    }

    /**
     * CSV usage report for {@code [fechaInicio, fechaFin]}, one row per calendar day (RN-ADM-04).
     * Columns: {@code fecha,reservas,ocupacion_pct,ingresos_total,ingresos_redsys,ingresos_cash}.
     * Only aggregates are emitted — no names, emails or any personal data (RN-RGPD-04). Numbers use a
     * dot decimal separator ({@link Locale#ROOT}) so the CSV parses independently of the server locale.
     *
     * @throws ValidationException when {@code fechaFin} precedes {@code fechaInicio}
     */
    @Transactional(readOnly = true)
    public String exportarCsv(LocalDate fechaInicio, LocalDate fechaFin) {
        validateRange(fechaInicio, fechaFin);

        Map<LocalDate, long[]> reservasByDay = reservationsByDay(fechaInicio, fechaFin);
        Map<LocalDate, BigDecimal[]> ingresosByDay = paymentsByDay(fechaInicio, fechaFin);
        boolean scheduled = scheduleConfigured();

        StringBuilder csv = new StringBuilder();
        csv.append(CSV_HEADER).append(CRLF);
        for (LocalDate day = fechaInicio; !day.isAfter(fechaFin); day = day.plusDays(1)) {
            long[] reservas = reservasByDay.getOrDefault(day, new long[2]);
            long count = reservas[0];
            int slotsReservadosDia = durationToSlots(reservas[1]);
            BigDecimal pct = scheduled
                    ? occupancyPct(slotsReservadosDia, SLOTS_PER_DAY)
                    : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

            BigDecimal[] ingresos = ingresosByDay.getOrDefault(day, zeroTriple());
            csv.append(day)
                    .append(',').append(count)
                    .append(',').append(String.format(Locale.ROOT, "%.1f", pct))
                    .append(',').append(String.format(Locale.ROOT, "%.2f", ingresos[0]))
                    .append(',').append(String.format(Locale.ROOT, "%.2f", ingresos[1]))
                    .append(',').append(String.format(Locale.ROOT, "%.2f", ingresos[2]))
                    .append(CRLF);
        }
        return csv.toString();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void validateRange(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            throw new ValidationException("fechaInicio and fechaFin are required");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new ValidationException("fechaFin must be on or after fechaInicio");
        }
    }

    private boolean scheduleConfigured() {
        return systemConfigRepositoryPort.findById(1L).isPresent();
    }

    private int slotsDisponibles(LocalDate fechaInicio, LocalDate fechaFin) {
        if (!scheduleConfigured()) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
        return (int) (days * SLOTS_PER_DAY);
    }

    /** Reserved slots from an aggregate duration; reservations are slot-aligned (design D2). */
    private int durationToSlots(long durationMinutes) {
        return (int) Math.round((double) durationMinutes / SLOT_MINUTES);
    }

    /** {@code reservados/disponibles*100} clamped to [0, 100] with one decimal; 0 when no slots. */
    private BigDecimal occupancyPct(int slotsReservados, int slotsDisponibles) {
        if (slotsDisponibles <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal pct = BigDecimal.valueOf(slotsReservados)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(slotsDisponibles), 1, RoundingMode.HALF_UP);
        return pct.compareTo(HUNDRED) > 0 ? HUNDRED.setScale(1, RoundingMode.HALF_UP) : pct;
    }

    private Map<LocalDate, long[]> reservationsByDay(LocalDate fechaInicio, LocalDate fechaFin) {
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Object[] row : reservationRepository.findActiveUsageRowsInRange(fechaInicio, fechaFin)) {
            LocalDate day = (LocalDate) row[0];
            long duration = ((Number) row[1]).longValue();
            long[] agg = byDay.computeIfAbsent(day, k -> new long[2]);
            agg[0] += 1;          // reservation count
            agg[1] += duration;   // accumulated duration in minutes
        }
        return byDay;
    }

    private Map<LocalDate, BigDecimal[]> paymentsByDay(LocalDate fechaInicio, LocalDate fechaFin) {
        OffsetDateTime start = startOfDay(fechaInicio);
        OffsetDateTime end = startOfDay(fechaFin.plusDays(1));
        Map<LocalDate, BigDecimal[]> byDay = new HashMap<>();
        for (Object[] row : paymentRepository.findPaidRowsInRange(start, end)) {
            OffsetDateTime paidAt = (OffsetDateTime) row[0];
            BigDecimal amount = money(row[1]);
            PaymentMethod method = (PaymentMethod) row[2];
            LocalDate day = paidAt.atZoneSameInstant(ZONE).toLocalDate();
            BigDecimal[] agg = byDay.computeIfAbsent(day, k -> zeroTriple());
            agg[0] = agg[0].add(amount);                       // total
            if (method == PaymentMethod.REDSYS) {
                agg[1] = agg[1].add(amount);                   // redsys
            } else if (method == PaymentMethod.CASH) {
                agg[2] = agg[2].add(amount);                   // cash
            }
        }
        return byDay;
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal[] zeroTriple() {
        return new BigDecimal[]{zeroMoney(), zeroMoney(), zeroMoney()};
    }

    private static BigDecimal money(Object value) {
        BigDecimal amount = value == null
                ? BigDecimal.ZERO
                : (value instanceof BigDecimal b ? b : new BigDecimal(value.toString()));
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
