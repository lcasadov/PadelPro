package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Reservation}.
 *
 * <p>The availability query fetches active reservations for a date (status {@code IN
 * ('PENDING_CONFIRMATION','CONFIRMED')}, RN-RES-01) and eagerly joins participants to avoid N+1
 * loads when counting seats. Backed by the partial index {@code idx_res_date_status}.
 */
@Repository
public interface ReservationJpaRepository extends JpaRepository<Reservation, UUID> {

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            LEFT JOIN FETCH r.participants
            WHERE r.reservationDate = :date
              AND r.status IN :statuses
            """)
    List<Reservation> findActiveByDateWithParticipants(@Param("date") LocalDate date,
                                                        @Param("statuses") List<ReservationStatus> statuses);
}
