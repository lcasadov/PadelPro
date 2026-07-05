package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    /** Single reservation with its participants eagerly fetched (detail / cancel paths). */
    @Query("""
            SELECT r FROM Reservation r
            LEFT JOIN FETCH r.participants
            WHERE r.id = :id
            """)
    java.util.Optional<Reservation> findByIdWithParticipants(@Param("id") UUID id);

    /**
     * Load a reservation locking its row ({@code SELECT ... FOR UPDATE}) for the atomic join (D2).
     * The root row alone is locked — participants are NOT join-fetched here so the lock stays on the
     * {@code reservations} row; the caller reads {@code getParticipants()} lazily inside the same
     * transaction, after the lock is held, so the seat count reflects the latest committed state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Reservations where the user is owner OR a registered participant (RN-AUTH-01), participants
     * fetched to avoid N+1. Payments are batch-loaded separately by the query service.
     */
    @Query("""
            SELECT DISTINCT r FROM Reservation r
            LEFT JOIN FETCH r.participants
            WHERE r.ownerId = :userId
               OR EXISTS (
                    SELECT 1 FROM Participant p
                    WHERE p.reservation = r AND p.userId = :userId
               )
            ORDER BY r.reservationDate DESC, r.startTime DESC
            """)
    List<Reservation> findVisibleToUserWithParticipants(@Param("userId") Long userId);

    /** All reservations with participants (ADMIN listing). */
    @Query("""
            SELECT DISTINCT r FROM Reservation r
            LEFT JOIN FETCH r.participants
            ORDER BY r.reservationDate DESC, r.startTime DESC
            """)
    List<Reservation> findAllWithParticipants();
}
