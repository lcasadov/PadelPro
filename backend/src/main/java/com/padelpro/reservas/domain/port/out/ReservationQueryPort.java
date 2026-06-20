package com.padelpro.reservas.domain.port.out;

import com.padelpro.reservas.domain.model.ReservationOccupancy;

import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for read-only reservation queries needed by the availability calculation.
 *
 * <p>The adapter is expected to use the partial index {@code idx_res_date_status WHERE status <>
 * 'CANCELLED'} and to return only active occupants (status {@code IN
 * ('PENDING_CONFIRMATION','CONFIRMED')}, RN-RES-01), each carrying its participant count so the
 * service can compute free slots without triggering N+1 loads.
 */
public interface ReservationQueryPort {

    /**
     * Active reservations on the given date together with their participant counts.
     *
     * @param date the date to query
     * @return list of occupancies (never {@code null}); empty when the date has no active reservations
     */
    List<ReservationOccupancy> findActiveOccupanciesByDate(LocalDate date);
}
