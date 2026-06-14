package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationOccupancy;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Outbound adapter implementing {@link ReservationQueryPort} on top of {@link ReservationJpaRepository}.
 *
 * <p>Maps each active reservation to a {@link ReservationOccupancy} carrying its participant count,
 * so the application service computes free slots without touching JPA entities or lazy collections.
 */
@Component
public class ReservationQueryAdapter implements ReservationQueryPort {

    /** Active occupants for availability (RN-RES-01): CANCELLED and COMPLETED are excluded. */
    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.PENDING_CONFIRMATION, ReservationStatus.CONFIRMED);

    private final ReservationJpaRepository repository;

    public ReservationQueryAdapter(ReservationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationOccupancy> findActiveOccupanciesByDate(LocalDate date) {
        List<Reservation> reservations = repository.findActiveByDateWithParticipants(date, ACTIVE_STATUSES);
        return reservations.stream()
                .map(r -> new ReservationOccupancy(
                        r.getStartTime(),
                        r.getEndTime(),
                        r.getParticipants().size()))
                .toList();
    }
}
