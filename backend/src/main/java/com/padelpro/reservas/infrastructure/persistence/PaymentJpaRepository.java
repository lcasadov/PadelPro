package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Payment} (data-model §3.5).
 *
 * <p>1:1 with {@code reservations}; {@link #findByReservationId(UUID)} backs the refund path on
 * cancellation. List queries that join payments to reservations live in {@link ReservationJpaRepository}.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReservationId(UUID reservationId);

    /** Batch-load payments for a set of reservations (anti-N+1 for list endpoints, §7.2). */
    List<Payment> findByReservationIdIn(Collection<UUID> reservationIds);

    /** Locate a payment by its unique Redsys order id (webhook idempotency, RN-PAY-02). */
    Optional<Payment> findByRedsysOrderId(String redsysOrderId);

    /**
     * Locate a payment by Redsys order id locking its row ({@code SELECT ... FOR UPDATE}) so two
     * concurrent webhook notifications for the same order cannot both transition IN_PROGRESS → PAID
     * (MEDIO-2). The second waits for the first to commit, then re-reads the already-PAID state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.redsysOrderId = :redsysOrderId")
    Optional<Payment> findByRedsysOrderIdForUpdate(@Param("redsysOrderId") String redsysOrderId);

    /** All payments most-recent first (ADMIN history, pagos-redsys-online group 5). */
    List<Payment> findAllByOrderByCreatedAtDesc();
}
